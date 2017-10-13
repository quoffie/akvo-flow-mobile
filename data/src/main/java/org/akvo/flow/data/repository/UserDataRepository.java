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

package org.akvo.flow.data.repository;

import org.akvo.flow.data.datasource.DataSourceFactory;
import org.akvo.flow.domain.repository.UserRepository;

import javax.inject.Inject;

import io.reactivex.Observable;

public class UserDataRepository implements UserRepository {


    private final DataSourceFactory dataSourceFactory;

    @Inject
    public UserDataRepository(DataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    @Override
    public Observable<Boolean> mobileSyncAllowed() {
        return dataSourceFactory.getSharedPreferencesDataSource().mobileSyncEnabled();
    }

    @Override
    public Observable<Boolean> keepScreenOn() {
        return dataSourceFactory.getSharedPreferencesDataSource().keepScreenOn();
    }

    @Override
    public Observable<String> getAppLanguage() {
        return dataSourceFactory.getSharedPreferencesDataSource().getAppLanguage();
    }

    @Override
    public Observable<Integer> getImageSize() {
        return dataSourceFactory.getSharedPreferencesDataSource().getImageSize();
    }

    @Override
    public Observable<String> getDeviceId() {
        return dataSourceFactory.getSharedPreferencesDataSource().getDeviceId();
    }

    @Override
    public Observable<Boolean> saveScreenOnPreference(Boolean keepScreenOn) {
        return dataSourceFactory.getSharedPreferencesDataSource().saveScreenOn(keepScreenOn);
    }

    @Override
    public Observable<Boolean> saveEnableMobileDataPreference(Boolean enable) {
        return dataSourceFactory.getSharedPreferencesDataSource().saveEnableMobileData(enable);
    }

    @Override
    public Observable<Boolean> saveLanguagePreference(String language) {
        return dataSourceFactory.getSharedPreferencesDataSource().saveLanguage(language);
    }

    @Override
    public Observable<Boolean> saveImageSizePreference(Integer size) {
        return dataSourceFactory.getSharedPreferencesDataSource().saveImageSize(size);
    }
}
