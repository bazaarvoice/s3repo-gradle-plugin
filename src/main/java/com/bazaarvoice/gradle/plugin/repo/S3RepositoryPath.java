package com.bazaarvoice.gradle.plugin.repo;

/**
 * Created by louis.williams on 7/2/15.
 */
public class S3RepositoryPath {

    private String bucketName;
    private String folderPath;

    public S3RepositoryPath(String s3url) {
        String[] parts = s3url.split("/");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Could not parse path " + s3url);
        }

        bucketName = parts[2];
        folderPath = "";

        for (int i = 3; i < parts.length; i++) {
            String part = parts[i];

            folderPath += part;

            if (part != null && i < parts.length - 1) {
                folderPath += "/";
            }

        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    @Override
    public String toString() {
        return "s3://" + bucketName + "/" + folderPath;
    }
}
