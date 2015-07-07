# S3 Repo Gradle Plugin

This plugin is strongly based of [s3repo-maven-plugin](https://github.com/bazaarvoice/s3repo-maven-plugin), and lets you deploy resources to a remote RPM repository hosted on Amazon S3.

## Requirements

* [Amazon credentials](http://docs.aws.amazon.com/AWSSdkDocsJava/latest//DeveloperGuide/credentials.html) (if not specified below)   
* The *createrepo* command is required to update the RPM metadata in a repo. There is a [*createrepo* program for OS X](https://github.com/stepanstipl/homebrew-noop), but it does not have support for the *--skip-stat* flag, which is required to prevent downloading and re-uploading an entire repository.

## Configuration

The following is a full example configuration:

    s3repo {
        allowCreateRepository = false  // Default. Creates repo metadata if it doesn't exist 
        forceDeploy = false  // Default. Overwrites already-deployed packages
        s3RepositoryUrl = "s3://bucket-name/path/to/yum-repo/"  // Path to root of repo
        s3AccessKey = "ACCESS-KEY"  // Overrides the default AWS provider chain
        s3SecretKey = "SECRET-KEY"  // Overrides the default AWS provider chain 
        inputs = files(buildRpm.outputs.files, shadowJar.outputs.files)  // Accepts a FileCollection of input files
    }

## Task: updateCreate

This task updates a repository, and creates it if *allowCreateRepository* is true.