package com.bazaarvoice.gradle.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class S3RepoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        /* Define arguments for task */
        project.getExtensions().create("s3repo", S3RepoExtension.class);

        /* Create task */
        project.getTasks().create("publishToS3Repo", UpdateRepoTask.class);
    }
}
