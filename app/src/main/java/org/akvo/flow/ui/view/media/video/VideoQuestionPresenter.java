/*
 * Copyright (C) 2018 Stichting Akvo (Akvo Foundation)
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

package org.akvo.flow.ui.view.media.video;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.akvo.flow.domain.interactor.CopyVideo;
import org.akvo.flow.domain.interactor.DefaultObserver;
import org.akvo.flow.domain.interactor.UseCase;
import org.akvo.flow.presentation.Presenter;
import org.akvo.flow.util.MediaFileHelper;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

public class VideoQuestionPresenter implements Presenter {

    private final UseCase copyVideo;
    private final MediaFileHelper mediaFileHelper;

    private IVideoQuestionView view;

    @Inject
    public VideoQuestionPresenter(@Named("copyVideo") UseCase copyVideo, MediaFileHelper mediaFileHelper) {
        this.copyVideo = copyVideo;
        this.mediaFileHelper = mediaFileHelper;
    }

    public void setView(IVideoQuestionView view) {
        this.view = view;
    }

    @Override
    public void destroy() {
        copyVideo.dispose();
    }

    public void onVideoReady(@Nullable String filePath) {
        if (!TextUtils.isEmpty(filePath)) {
            view.showLoading();
            final String targetVideoFilePath = mediaFileHelper.getVideoFilePath();
            Map<String, Object> params = new HashMap<>(4);
            params.put(CopyVideo.ORIGIN_FILE_NAME_PARAM, filePath);
            params.put(CopyVideo.DESTINATION_FILE_NAME_PARAM, targetVideoFilePath);
            copyVideo.execute(new DefaultObserver<Boolean>() {
                @Override
                public void onNext(Boolean aBoolean) {
                    view.displayThumbnail(targetVideoFilePath);
                }

                @Override
                public void onError(Throwable e) {
                    view.showErrorGettingMedia();
                }
            }, params);
        } else {
            view.showErrorGettingMedia();
        }
    }
}
