/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2019 Alice Gaudon <alice@gaudon.pro>
 * SPDX-FileCopyrightText: 2018 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2017 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2016 ownCloud Inc.
 * SPDX-FileCopyrightText: 2014 Luke Owncloud <owncloud@ohrt.org>
 * SPDX-FileCopyrightText: 2015-2016 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-FileCopyrightText: 2015-2016 María Asensio Valverde <masensio@solidgear.es>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.db;

import android.os.Parcel;
import android.os.Parcelable;

import com.nextcloud.client.account.User;
import com.nextcloud.client.jobs.upload.FileUploadHelper;
import com.nextcloud.client.jobs.upload.FileUploadWorker;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.UploadsStorageManager;
import com.owncloud.android.datamodel.UploadsStorageManager.UploadStatus;
import com.owncloud.android.files.services.NameCollisionPolicy;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.operations.UploadFileOperation;
import com.owncloud.android.utils.MimeTypeUtil;

import java.io.File;

import androidx.annotation.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Stores all information in order to start upload operations. PersistentUploadObject can
 * be stored persistently by {@link UploadsStorageManager}.
 */
public class OCUpload implements Parcelable {

    private static final String TAG = OCUpload.class.getSimpleName();

    private long uploadId;

    /**
     * Absolute path in the local file system to the file to be uploaded.
     */
    private String localPath;

    /**
     * Absolute path in the remote account to set to the uploaded file (not for its parent folder!)
     */
    private String remotePath;

    /**
     * Name of Owncloud account to upload file to.
     */
    private String accountName;

    /**
     * File size.
     */
    private long fileSize;

    /**
     * Local action for upload. (0 - COPY, 1 - MOVE, 2 - FORGET)
     */
    private int localAction;

    /**
     * What to do in case of name collision.
     */
    private NameCollisionPolicy nameCollisionPolicy;

    /**
     * Create destination folder?
     */
    private boolean createRemoteFolder;

    /**
     * Status of upload (later, in_progress, ...).
     */
    private UploadStatus uploadStatus;

    /**
     * Result from last upload operation. Can be null.
     */
    private UploadResult lastResult;

    /**
     * Defines the origin of the upload; see constants CREATED_ in {@link UploadFileOperation}
     */
    private int createdBy;

    /**
     * When the upload ended
     */
    private long uploadEndTimestamp;

    /**
     * Upload only via wifi?
     */
    private boolean useWifiOnly;

    /**
     * Upload only if phone being charged?
     */
    private boolean whileChargingOnly;

    /**
     * Token to unlock E2E folder
     */
    private String folderUnlockToken;

    /**
     * temporary values, used for sorting
     */
    private UploadStatus fixedUploadStatus;
    private boolean fixedUploadingNow;
    private long fixedUploadEndTimeStamp;
    private long fixedUploadId;

    /**
     * Main constructor.
     *
     * @param localPath         Absolute path in the local file system to the file to be uploaded.
     * @param remotePath        Absolute path in the remote account to set to the uploaded file.
     * @param accountName       Name of an file owner account.
     */
    public OCUpload(String localPath, String remotePath, String accountName) {
        if (localPath == null || !localPath.startsWith(File.separator)) {
            throw new IllegalArgumentException("Local path must be an absolute path in the local file system");
        }
        if (remotePath == null || !remotePath.startsWith(OCFile.PATH_SEPARATOR)) {
            throw new IllegalArgumentException("Remote path must be an absolute path in the local file system");
        }
        if (accountName == null || accountName.length() < 1) {
            throw new IllegalArgumentException("Invalid account name");
        }
        resetData();
        this.localPath = localPath;
        this.remotePath = remotePath;
        this.accountName = accountName;
    }

    /**
     * Convenience constructor to re-upload already existing {@link OCFile}s.
     *
     * @param  ocFile           {@link OCFile} instance to update in the remote server.
     * @param  user             file owner
     */
    public OCUpload(OCFile ocFile, User user) {
        this(ocFile.getStoragePath(), ocFile.getRemotePath(), user.getAccountName());
    }

    /**
     * Reset all the fields to default values.
     */
    private void resetData() {
        remotePath = "";
        localPath = "";
        accountName = "";
        fileSize = -1;
        uploadId = -1;
        localAction = FileUploadWorker.LOCAL_BEHAVIOUR_COPY;
        nameCollisionPolicy = NameCollisionPolicy.DEFAULT;
        createRemoteFolder = false;
        uploadStatus = UploadStatus.UPLOAD_IN_PROGRESS;
        lastResult = UploadResult.UNKNOWN;
        createdBy = UploadFileOperation.CREATED_BY_USER;
        useWifiOnly = true;
        whileChargingOnly = false;
        folderUnlockToken = "";
    }

    public void setDataFixed(FileUploadHelper uploadHelper) {
        fixedUploadStatus = uploadStatus;
        fixedUploadingNow = uploadHelper != null && uploadHelper.isUploadingNow(this);
        fixedUploadEndTimeStamp = uploadEndTimestamp;
        fixedUploadId = uploadId;
    }

    // custom Getters & Setters
    /**
     * Sets uploadStatus AND SETS lastResult = null;
     * @param uploadStatus the uploadStatus to set
     */
    public void setUploadStatus(UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
        setLastResult(UploadResult.UNKNOWN);
    }

    /**
     * @param lastResult the lastResult to set
     */
    public void setLastResult(UploadResult lastResult) {
        this.lastResult = lastResult != null ? lastResult : UploadResult.UNKNOWN;
    }

    public UploadStatus getFixedUploadStatus() {
        return fixedUploadStatus;
    }

    public boolean isFixedUploadingNow() {
        return fixedUploadingNow;
    }

    public long getFixedUploadEndTimeStamp() {
        return fixedUploadEndTimeStamp;
    }

    public long getFixedUploadId() {
        return fixedUploadId;
    }

    /**
     * @return the mimeType
     */
    public String getMimeType() {
        return MimeTypeUtil.getBestMimeTypeByFilename(localPath);
    }

    /**
     * For debugging purposes only.
     */
    public String toFormattedString() {
        try {
            String localPath = getLocalPath() != null ? getLocalPath() : "";
            return localPath + " status:" + getUploadStatus() + " result:" +
                    (getLastResult() == null ? "null" : getLastResult().getValue());
        } catch (NullPointerException e) {
            Log_OC.d(TAG, "Exception", e);
            return e.toString();
        }
    }

    /****
     *
     */
    public static final Parcelable.Creator<OCUpload> CREATOR = new Parcelable.Creator<>() {

        @Override
        public OCUpload createFromParcel(Parcel source) {
            return new OCUpload(source);
        }

        @Override
        public OCUpload[] newArray(int size) {
            return new OCUpload[size];
        }
    };

    /**
     * Reconstruct from parcel
     *
     * @param source The source parcel
     */
    private OCUpload(Parcel source) {
        readFromParcel(source);
    }

    private void readFromParcel(Parcel source) {
        uploadId = source.readLong();
        localPath = source.readString();
        remotePath = source.readString();
        accountName = source.readString();
        localAction = source.readInt();
        nameCollisionPolicy = NameCollisionPolicy.deserialize(source.readInt());
        createRemoteFolder = source.readInt() == 1;
        try {
            uploadStatus = UploadStatus.valueOf(source.readString());
        } catch (IllegalArgumentException x) {
            uploadStatus = UploadStatus.UPLOAD_IN_PROGRESS;
        }
        uploadEndTimestamp = source.readLong();
        try {
            lastResult = UploadResult.valueOf(source.readString());
        } catch (IllegalArgumentException x) {
            lastResult = UploadResult.UNKNOWN;
        }
        createdBy = source.readInt();
        useWifiOnly = source.readInt() == 1;
        whileChargingOnly = source.readInt() == 1;
        folderUnlockToken = source.readString();
    }

    @Override
    public int describeContents() {
        return this.hashCode();
    }

    @SuppressFBWarnings("SEO_SUBOPTIMAL_EXPRESSION_ORDER")
    public boolean isSame(@Nullable Object obj) {
        if (!(obj instanceof OCUpload other)) {
            return false;
        }
        return this.uploadId == other.uploadId &&
            localPath.equals(other.localPath) &&
            remotePath.equals(other.remotePath) &&
            accountName.equals(other.accountName) &&
            localAction == other.localAction &&
            nameCollisionPolicy == other.nameCollisionPolicy &&
            createRemoteFolder == other.createRemoteFolder &&
            uploadStatus == other.uploadStatus &&
            lastResult == other.lastResult &&
            createdBy == other.createdBy &&
            useWifiOnly == other.useWifiOnly &&
            whileChargingOnly == other.whileChargingOnly &&
            folderUnlockToken.equals(other.folderUnlockToken);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(uploadId);
        dest.writeString(localPath);
        dest.writeString(remotePath);
        dest.writeString(accountName);
        dest.writeInt(localAction);
        dest.writeInt(nameCollisionPolicy.serialize());
        dest.writeInt(createRemoteFolder ? 1 : 0);
        dest.writeString(uploadStatus.name());
        dest.writeLong(uploadEndTimestamp);
        dest.writeString(lastResult == null ? "" : lastResult.name());
        dest.writeInt(createdBy);
        dest.writeInt(useWifiOnly ? 1 : 0);
        dest.writeInt(whileChargingOnly ? 1 : 0);
        dest.writeString(folderUnlockToken);
    }

    public long getUploadId() {
        return this.uploadId;
    }

    public String getLocalPath() {
        return this.localPath;
    }

    public String getRemotePath() {
        return this.remotePath;
    }

    public String getAccountName() {
        return this.accountName;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public int getLocalAction() {
        return this.localAction;
    }

    public NameCollisionPolicy getNameCollisionPolicy() {
        return this.nameCollisionPolicy;
    }

    public boolean isCreateRemoteFolder() {
        return this.createRemoteFolder;
    }

    public UploadStatus getUploadStatus() {
        return this.uploadStatus;
    }

    public UploadResult getLastResult() {
        return this.lastResult;
    }

    public int getCreatedBy() {
        return this.createdBy;
    }

    public long getUploadEndTimestamp() {
        return this.uploadEndTimestamp;
    }

    public boolean isUseWifiOnly() {
        return this.useWifiOnly;
    }

    public boolean exists() {
        return new File(localPath).exists();
    }

    public boolean isWhileChargingOnly() {
        return this.whileChargingOnly;
    }

    public String getFolderUnlockToken() {
        return this.folderUnlockToken;
    }

    public void setUploadId(long uploadId) {
        this.uploadId = uploadId;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setLocalAction(int localAction) {
        this.localAction = localAction;
    }

    public void setNameCollisionPolicy(NameCollisionPolicy nameCollisionPolicy) {
        this.nameCollisionPolicy = nameCollisionPolicy;
    }

    public void setCreateRemoteFolder(boolean createRemoteFolder) {
        this.createRemoteFolder = createRemoteFolder;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public void setUploadEndTimestamp(long uploadEndTimestamp) {
        this.uploadEndTimestamp = uploadEndTimestamp;
    }

    public void setUseWifiOnly(boolean useWifiOnly) {
        this.useWifiOnly = useWifiOnly;
    }

    public void setWhileChargingOnly(boolean whileChargingOnly) {
        this.whileChargingOnly = whileChargingOnly;
    }

    public void setFolderUnlockToken(String folderUnlockToken) {
        this.folderUnlockToken = folderUnlockToken;
    }
}
