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

package org.akvo.flow.util.image;

import android.app.Activity;
import android.content.Context;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class PicassoImageLoader implements ImageLoader<PicassoImageTarget> {

    private final Picasso requestManager;

    public PicassoImageLoader(Context context) {
        requestManager = new Picasso.Builder(context)
                .addRequestHandler(new VideoRequestHandler())
                .addRequestHandler(new Base64RequestHandler())
                .build();
    }

    public PicassoImageLoader(Activity activity) {
        requestManager =  new Picasso.Builder(activity)
                .addRequestHandler(new VideoRequestHandler())
                .addRequestHandler(new Base64RequestHandler())
                .build();
    }

    @Override
    public void loadFromFile(File file, PicassoImageTarget target) {
        requestManager.load(file).into(target);
    }

    @Override
    public void loadFromFile(File file, ImageView imageView) {
        requestManager.load(file).into(imageView);
    }

    @Override
    public void loadVideoThumbnail(String filepath, ImageView imageView) {
        requestManager.load(VideoRequestHandler.SCHEME_VIDEO + ":" + filepath).into(imageView);
    }

    @Override
    public void loadFromBase64String(String image, ImageView imageView,
            final ImageLoaderListener listener) {
        requestManager.load(Base64RequestHandler.SCHEME_BASE64 + ":" + image).into(imageView,
                new Callback() {
                    @Override
                    public void onSuccess() {
                        listener.onImageReady(null);
                    }

                    @Override
                    public void onError() {
                        //Empty
                    }
                });

    }

    @Override
    public void clearImage(File imageFile) {
        requestManager.invalidate(imageFile);
    }
}
