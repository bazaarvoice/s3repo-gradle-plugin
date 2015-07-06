package com.bazaarvoice.gradle.plugin;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.bazaarvoice.gradle.plugin.repo.LocalS3Repo;
import com.bazaarvoice.gradle.plugin.repo.S3RepositoryPath;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * Created by louis.williams on 7/1/15.
 */
public class UpdateCreateTask extends DefaultTask {

    private static final String STAGING_DIRECTORY = "_s3repo_staging";

    private static Logger log = Logging.getLogger(UpdateCreateTask.class.getName());

    private S3RepoExtension extension;
    private S3RepositoryPath repositoryPath;
    private AmazonS3Client s3Client;

    private LocalS3Repo s3RepoStage;

    @TaskAction
    public void updateCreate() throws Exception {

        /* Get extension */
        extension = (S3RepoExtension) getProject().getExtensions().getByName("s3repo");

        s3Client = createS3Client();

        repositoryPath = getS3Bucket();

        log.info("Using " + repositoryPath);

        /* Downloads, stages, and verifies the S3 repo */
        s3RepoStage = new LocalS3Repo(s3Client, repositoryPath, STAGING_DIRECTORY, extension.allowCreateRepo);
        s3RepoStage.verifyRepo();

        /* Stage our input files for upload */
        s3RepoStage.stageInputFiles(extension.inputs, extension.forceDeploy);

        /* Update or create remote repo */
        s3RepoStage.updateOrCreateRemoteRepo();


    }

    private AmazonS3Client createS3Client() {
        if (extension.s3AcessKey != null && extension.s3SecretKey != null) {
            return new AmazonS3Client(new BasicAWSCredentials(extension.s3AcessKey, extension.s3SecretKey));
        } else {
            return new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
        }
    }

    private S3RepositoryPath getS3Bucket() {
        if (extension.s3RepositoryUrl != null) {
            return new S3RepositoryPath(extension.s3RepositoryUrl);
        } else {
            throw new IllegalArgumentException("s3RespositioryUrl must be declared");
        }
    }


}
