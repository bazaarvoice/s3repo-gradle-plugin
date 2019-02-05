package com.bazaarvoice.gradle.plugin;

import org.gradle.api.file.FileCollection;


/**
 * Created by louis.williams on 7/1/15.
 */
public class S3RepoExtension {
    public boolean allowCreateRepo;
    public boolean forcePublish;
    public boolean recreateRepo;
    public boolean skipUpload;
    public boolean skipUpdate;
    public String s3RepositoryUrl;
    public String s3AccessKey;
    public String s3SecretKey;
    public FileCollection inputs;
    public String createRepoArgs;
}
