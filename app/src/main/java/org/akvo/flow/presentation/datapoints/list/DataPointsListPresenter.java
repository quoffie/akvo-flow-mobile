/*
 * Copyright (C) 2017 Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Flow.
 *
 * Akvo Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Flow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Flow.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.akvo.flow.presentation.datapoints.list;

import android.support.annotation.NonNull;

import org.akvo.flow.domain.SurveyGroup;
import org.akvo.flow.domain.entity.DataPoint;
import org.akvo.flow.domain.entity.SyncResult;
import org.akvo.flow.domain.interactor.DefaultFlowableObserver;
import org.akvo.flow.domain.interactor.DefaultObserver;
import org.akvo.flow.domain.interactor.ErrorComposable;
import org.akvo.flow.domain.interactor.GetSavedDataPoints;
import org.akvo.flow.domain.interactor.SyncDataPoints;
import org.akvo.flow.domain.interactor.UseCase;
import org.akvo.flow.presentation.Presenter;
import org.akvo.flow.presentation.datapoints.list.entity.ListDataPoint;
import org.akvo.flow.presentation.datapoints.list.entity.ListDataPointMapper;
import org.akvo.flow.util.ConstantUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import timber.log.Timber;

import static org.akvo.flow.domain.entity.SyncResult.ResultCode.SUCCESS;

public class DataPointsListPresenter implements Presenter {

    private final UseCase getSavedDataPoints;
    private final SyncDataPoints syncDataPoints;
    private final UseCase allowedToConnect;
    private final ListDataPointMapper mapper;

    private DataPointsListView view;
    private SurveyGroup surveyGroup;
    private int orderBy = ConstantUtil.ORDER_BY_DATE;
    private Double latitude;
    private Double longitude;

    @Inject
    DataPointsListPresenter(@Named("getSavedDataPoints") UseCase getSavedDataPoints,
            ListDataPointMapper mapper, SyncDataPoints syncDataPoints,
            @Named("allowedToConnect") UseCase allowedToConnect) {
        this.getSavedDataPoints = getSavedDataPoints;
        this.mapper = mapper;
        this.syncDataPoints = syncDataPoints;
        this.allowedToConnect = allowedToConnect;
    }

    public void setView(@NonNull DataPointsListView view) {
        this.view = view;
    }

    void onDataReady(SurveyGroup surveyGroup) {
        this.surveyGroup = surveyGroup;
        if (surveyGroup == null) {
            view.hideMenu();
        } else {
            if (surveyGroup.isMonitored()) {
                view.showMonitoredMenu();
            } else {
                view.showNonMonitoredMenu();
            }
        }
    }

    void loadDataPoints() {
        getSavedDataPoints.dispose();
        if (surveyGroup != null) {
            Map<String, Object> params = new HashMap<>(8);
            params.put(GetSavedDataPoints.KEY_SURVEY_GROUP_ID, surveyGroup.getId());
            params.put(GetSavedDataPoints.KEY_ORDER_BY, orderBy);
            params.put(GetSavedDataPoints.KEY_LATITUDE, latitude);
            params.put(GetSavedDataPoints.KEY_LONGITUDE, longitude);
            getSavedDataPoints.execute(new DefaultObserver<List<DataPoint>>() {

                @Override
                public void onError(Throwable e) {
                    Timber.e(e, "Error loading saved datapoints");
                    view.displayData(Collections.EMPTY_LIST);
                    view.showNoDataPoints(surveyGroup.isMonitored());
                }

                @Override
                public void onNext(List<DataPoint> dataPoints) {
                    List<ListDataPoint> mapDataPoints = mapper.transform(dataPoints);
                    view.displayData(mapDataPoints);
                    if (mapDataPoints.isEmpty()) {
                        view.showNoDataPoints(surveyGroup.isMonitored());
                    }
                }
            }, params);
        } else {
            noSurveySelected();
        }
    }

    void getFilteredDataPoints(String filter) {
        getSavedDataPoints.dispose();
        if (surveyGroup != null) {
            Map<String, Object> params = new HashMap<>(8);
            params.put(GetSavedDataPoints.KEY_SURVEY_GROUP_ID, surveyGroup.getId());
            params.put(GetSavedDataPoints.KEY_ORDER_BY, orderBy);
            params.put(GetSavedDataPoints.KEY_LATITUDE, latitude);
            params.put(GetSavedDataPoints.KEY_LONGITUDE, longitude);
            params.put(GetSavedDataPoints.KEY_FILTER, filter);
            getSavedDataPoints.execute(new DefaultObserver<List<DataPoint>>() {

                @Override
                public void onError(Throwable e) {
                    Timber.e(e, "Error loading saved datapoints");
                    view.displayData(Collections.EMPTY_LIST);
                    view.displayNoSearchResultsFound();
                }

                @Override
                public void onNext(List<DataPoint> dataPoints) {
                    List<ListDataPoint> mapDataPoints = mapper.transform(dataPoints);
                    view.displayData(mapDataPoints);
                    if (mapDataPoints.isEmpty()) {
                        view.displayNoSearchResultsFound();
                    }
                }
            }, params);
        } else {
            noSurveySelected();
        }
    }

    @Override
    public void destroy() {
        getSavedDataPoints.dispose();
        syncDataPoints.dispose();
        allowedToConnect.dispose();
    }

    void onSyncRecordsPressed() {
        if (surveyGroup != null) {
            view.showLoading();
            syncRecords(surveyGroup.getId());
        }
    }

    private void syncRecords(final long surveyGroupId) {
        allowedToConnect.execute(new DefaultObserver<Boolean>() {
            @Override
            public void onError(Throwable e) {
                Timber.e(e); //should not happen
            }

            @Override
            public void onNext(Boolean aBoolean) {
                if (aBoolean == null || !aBoolean) {
                    view.hideLoading();
                    view.showErrorSyncNotAllowed();
                } else {
                    sync(surveyGroupId);
                }
            }
        }, null);

    }

    private void sync(final long surveyGroupId) {
        Map<String, Object> params = new HashMap<>(2);
        params.put(SyncDataPoints.KEY_SURVEY_GROUP_ID, surveyGroupId);
        syncDataPoints.execute(new DefaultFlowableObserver<SyncResult>() {
            @Override
            public void onComplete() {
                view.hideLoading();
            }

            @Override
            public void onNext(SyncResult result) {
                if (result.getResultCode() == SUCCESS) {
                    if (result.getNumberOfSyncedItems() > 0) {
                        view.showSyncedResults(result.getNumberOfSyncedItems());
                    } else {
                        view.showNoDataPointsToSync();
                    }
                } else {
                    switch (result.getResultCode()) {
                        case ERROR_NO_NETWORK:
                            view.showErrorNoNetwork();
                            break;
                        case ERROR_ASSIGNMENT_MISSING:
                            view.showErrorAssignmentMissing();
                            break;
                        default:
                            view.showErrorSync();
                            break;
                    }
                }
            }
        }, new ErrorComposable() {
            @Override
            public void onError(Throwable e) {
                Timber.e(e, "Error syncing %s", surveyGroupId);
                view.hideLoading();
                view.showErrorSync();
            }
        }, params);
    }

    void onOrderByClick(int order) {
        if (orderBy != order) {
            if (order == ConstantUtil.ORDER_BY_DISTANCE && (latitude == null
                    || longitude == null)) {
                // Warn user that the location is unknown
                view.showErrorMissingLocation();
                return;
            }
            this.orderBy = order;
            loadDataPoints();
        }
    }

    void onLocationReady(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    void onOrderByClicked() {
        view.showOrderByDialog(orderBy);
    }

    void onNewSurveySelected(SurveyGroup surveyGroup) {
        getSavedDataPoints.dispose();
        syncDataPoints.dispose();
        view.hideLoading();
        onDataReady(surveyGroup);
        loadDataPoints();
    }

    private void noSurveySelected() {
        view.displayData(Collections.EMPTY_LIST);
        view.showNoSurveySelected();
    }
}
