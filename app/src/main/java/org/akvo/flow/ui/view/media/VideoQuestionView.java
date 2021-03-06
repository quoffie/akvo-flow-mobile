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

package org.akvo.flow.ui.view.media;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.akvo.flow.R;
import org.akvo.flow.async.MediaSyncTask;
import org.akvo.flow.domain.Question;
import org.akvo.flow.domain.QuestionResponse;
import org.akvo.flow.domain.response.value.Media;
import org.akvo.flow.event.QuestionInteractionEvent;
import org.akvo.flow.event.SurveyListener;
import org.akvo.flow.injector.component.DaggerViewComponent;
import org.akvo.flow.injector.component.ViewComponent;
import org.akvo.flow.presentation.SnackBarManager;
import org.akvo.flow.serialization.response.value.MediaValue;
import org.akvo.flow.ui.Navigator;
import org.akvo.flow.ui.view.QuestionView;
import org.akvo.flow.util.ConstantUtil;
import org.akvo.flow.util.FileUtil;
import org.akvo.flow.util.image.ImageLoader;
import org.akvo.flow.util.image.ImageLoaderListener;
import org.akvo.flow.util.image.PicassoImageLoader;

import java.io.File;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Question type that supports taking a video recording
 *
 * @author Christopher Fagiani
 */
public class VideoQuestionView extends QuestionView implements MediaSyncTask.DownloadListener {

    @Inject
    SnackBarManager snackBarManager;

    @Inject
    Navigator navigator;

    @BindView(R.id.media_btn)
    Button mMediaButton;

    @BindView(R.id.image)
    ImageView mImageView;

    @BindView(R.id.media_progress)
    ProgressBar mProgressBar;

    @BindView(R.id.media_download)
    View mDownloadBtn;

    private ImageLoader imageLoader;
    private String filename;

    public VideoQuestionView(Context context, Question q, SurveyListener surveyListener) {
        super(context, q, surveyListener);
        init();
    }

    private void init() {
        setQuestionView(R.layout.media_question_view);
        initialiseInjector();
        ButterKnife.bind(this);

        imageLoader = new PicassoImageLoader(getContext());
        mMediaButton.setText(R.string.takevideo);
        if (isReadOnly()) {
            mMediaButton.setVisibility(GONE);
        }
    }

    private void initialiseInjector() {
        ViewComponent viewComponent =
                DaggerViewComponent.builder().applicationComponent(getApplicationComponent())
                        .build();
        viewComponent.inject(this);
    }

    private void hideDownloadOptions() {
        mProgressBar.setVisibility(View.GONE);
        mDownloadBtn.setVisibility(View.GONE);
    }

    @OnClick(R.id.image)
    void onVideoViewClicked() {
        if (TextUtils.isEmpty(filename) || !(new File(filename).exists())) {
            showImageLoadError();
            return;
        }
        navigator.navigateToVideoView(getContext(), filename);
    }

    @OnClick(R.id.media_btn)
    void onTakeVideoClicked() {
        notifyQuestionListeners(QuestionInteractionEvent.TAKE_VIDEO_EVENT);
    }

    @OnClick(R.id.media_download)
    void onVideoDownloadClick() {
        mDownloadBtn.setVisibility(GONE);
        mProgressBar.setVisibility(VISIBLE);

        MediaSyncTask downloadTask = new MediaSyncTask(getContext(),
                new File(filename), this);
        downloadTask.execute();
    }

    /**
     * display the completion icon and install the response in the question
     * object
     */
    @Override
    public void questionComplete(Bundle mediaData) {
        String mediaFilePath =
                mediaData != null ? mediaData.getString(ConstantUtil.MEDIA_FILE_KEY) : null;
        if (mediaFilePath != null && new File(mediaFilePath).exists()) {
            filename = mediaFilePath;

            captureResponse();
            displayThumbnail();

        } else {
            snackBarManager.displaySnackBar(this, R.string.error_getting_media, getContext());
        }
    }

    /**
     * restores the file path for the file and turns on the complete icon if the
     * file exists
     */
    @Override
    public void rehydrate(QuestionResponse resp) {
        super.rehydrate(resp);

        if (resp == null || TextUtils.isEmpty(resp.getValue())) {
            return;
        }

        Media mMedia = MediaValue.deserialize(resp.getValue());
        filename = mMedia == null ? null : mMedia.getFilename();

        displayThumbnail();
        if (TextUtils.isEmpty(filename)) {
            return;
        }

        // We now check whether the file is found in the local filesystem, and update the path if it's not
        File file = new File(filename);
        if (!file.exists() && isReadOnly()) {
            // Looks like the image is not present in the filesystem (i.e. remote URL)
            // Update response, matching the local path. Note: In the future, media responses should
            // not leak filesystem paths, for these are not guaranteed to be homogeneous in all devices.
            file = new File(FileUtil.getFilesDir(FileUtil.FileType.MEDIA), file.getName());
            filename = file.getAbsolutePath();
            captureResponse();
        }
    }

    /**
     * clears the file path and the complete icon
     */
    @Override
    public void resetQuestion(boolean fireEvent) {
        super.resetQuestion(fireEvent);
        filename = null;
        mImageView.setImageDrawable(null);
        hideDownloadOptions();
    }

    @Override
    public void captureResponse(boolean suppressListeners) {
        QuestionResponse response = null;
        if (!TextUtils.isEmpty(filename)) {
            Question question = getQuestion();
            Media media = new Media();
            media.setFilename(filename);
            String value = MediaValue.serialize(media);
            response = new QuestionResponse.QuestionResponseBuilder()
                    .setValue(value)
                    .setType(ConstantUtil.VIDEO_RESPONSE_TYPE)
                    .setQuestionId(question.getQuestionId())
                    .setIteration(question.getIteration())
                    .setFilename(filename)
                    .createQuestionResponse();
        }
        setResponse(response);
    }

    private void displayThumbnail() {
        hideDownloadOptions();

        if (TextUtils.isEmpty(filename)) {
            return;
        }
        if (!new File(filename).exists()) {
            showTemporaryMedia();
        } else {
            displayVideoThumbnail(filename);
        }
    }

    private void showTemporaryMedia() {
        mImageView.setImageResource(R.drawable.blurry_image);
        mDownloadBtn.setVisibility(VISIBLE);
    }

    private void displayVideoThumbnail(String filename) {
        imageLoader.loadVideoThumbnail(filename, mImageView, new ImageLoaderListener() {
            @Override
            public void onImageReady() {
                // Empty
            }

            @Override
            public void onImageError() {
                showTemporaryMedia();
                showImageLoadError();
            }
        });
    }

    @Override
    public void onResourceDownload(boolean done) {
        if (!done) {
            showImageLoadError();
        }
        displayThumbnail();
    }

    private void showImageLoadError() {
        snackBarManager.displaySnackBar(this, R.string.error_video_preview, getContext());
    }
}
