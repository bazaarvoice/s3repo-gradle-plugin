package com.bazaarvoice.gradle.plugin;

import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;

import java.util.Collection;

/**
 * Created by louis.williams on 7/1/15.
 */
public class S3RepoExtension {
    public boolean allowCreateRepo;
    public boolean forceDeploy;
    public String s3RepositoryUrl;
    public String s3AccessKey;
    public String s3SecretKey;
    public FileCollection inputs;
}
