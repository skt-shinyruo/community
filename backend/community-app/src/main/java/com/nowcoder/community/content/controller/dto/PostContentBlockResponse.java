package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.PostContentBlockResult;
import com.nowcoder.community.content.application.result.PostMediaViewResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostContentBlockResponse {

    private UUID id;
    private int index;
    private String type;
    private String text;
    private UUID assetId;
    private String language;
    private String caption;
    private String displayName;
    private Map<String, Object> metadata;
    private PostMediaView media;

    public static PostContentBlockResponse from(PostContentBlockResult result) {
        if (result == null) {
            return null;
        }
        PostContentBlockResponse response = new PostContentBlockResponse();
        response.setId(result.id());
        response.setIndex(result.index());
        response.setType(result.type());
        response.setText(result.text());
        response.setAssetId(result.assetId());
        response.setLanguage(result.language());
        response.setCaption(result.caption());
        response.setDisplayName(result.displayName());
        response.setMetadata(result.metadata());
        response.setMedia(PostMediaView.from(result.media()));
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public PostMediaView getMedia() {
        return media;
    }

    public void setMedia(PostMediaView media) {
        this.media = media;
    }

    public static class PostMediaView {
        private UUID assetId;
        private String mediaKind;
        private String lifecycle;
        private String videoState;
        private String fileName;
        private String contentType;
        private long contentLength;
        private String url;
        private String downloadUrl;
        private String posterUrl;
        private List<PostMediaViewResult.VideoSource> sources;

        public static PostMediaView from(PostMediaViewResult result) {
            if (result == null) {
                return null;
            }
            PostMediaView view = new PostMediaView();
            view.setAssetId(result.assetId());
            view.setMediaKind(result.mediaKind());
            view.setLifecycle(result.lifecycle());
            view.setVideoState(result.videoState());
            view.setFileName(result.fileName());
            view.setContentType(result.contentType());
            view.setContentLength(result.contentLength());
            view.setUrl(result.url());
            view.setDownloadUrl(result.downloadUrl());
            view.setPosterUrl(result.posterUrl());
            view.setSources(result.sources());
            return view;
        }

        public UUID getAssetId() {
            return assetId;
        }

        public void setAssetId(UUID assetId) {
            this.assetId = assetId;
        }

        public String getMediaKind() {
            return mediaKind;
        }

        public void setMediaKind(String mediaKind) {
            this.mediaKind = mediaKind;
        }

        public String getLifecycle() {
            return lifecycle;
        }

        public void setLifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
        }

        public String getVideoState() {
            return videoState;
        }

        public void setVideoState(String videoState) {
            this.videoState = videoState;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getContentLength() {
            return contentLength;
        }

        public void setContentLength(long contentLength) {
            this.contentLength = contentLength;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public String getPosterUrl() {
            return posterUrl;
        }

        public void setPosterUrl(String posterUrl) {
            this.posterUrl = posterUrl;
        }

        public List<PostMediaViewResult.VideoSource> getSources() {
            return sources;
        }

        public void setSources(List<PostMediaViewResult.VideoSource> sources) {
            this.sources = sources;
        }
    }
}
