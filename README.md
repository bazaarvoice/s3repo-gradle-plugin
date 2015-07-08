# S3 Repo Gradle Plugin

This plugin is strongly based off of [s3repo-maven-plugin](https://github.com/bazaarvoice/s3repo-maven-plugin), and lets you deploy resources to a private RPM repository hosted on Amazon S3.

## Requirements

* The plugin uses the [Amazon provider chain credentials](http://docs.aws.amazon.com/AWSSdkDocsJava/latest//DeveloperGuide/credentials.html) (if not explicitly stated from below)   
* The *createrepo* binary is required to update the RPM metadata in a repo. There is a [*createrepo* program for OS X](https://github.com/stepanstipl/homebrew-noop), but it does not have support for the *--skip-stat* flag, which is required to prevent downloading and re-uploading an entire repository.

## Configuration

The following is a full configuration example for *build.gradle*:

    s3repo {
        inputs = files(buildRpm.outputs.files)                      // Accepts a FileCollection of input files
        s3RepositoryUrl = "s3://bucket-name/path/to/yum-repo/"      // Path to root of repo
        createRepoArgs = "--arg1 --arg2"                            // Extra arguments for the createrepo command
        s3AccessKey = "ACCESS-KEY"                                  // Overrides the default AWS provider chain
        s3SecretKey = "SECRET-KEY"                                  // Overrides the default AWS provider chain 
        allowCreateRepository = false                               // Default. Creates repo metadata if it doesn't exist 
        forceDeploy = false                                         // Default. Overwrites already-deployed packages
        skipUpdate = false                                          // Default. Skips updating the repository metadata
        skipUpload = false                                          // Default. Skips the upload to S3
    }

## Task: publishToS3Repo

This task updates a repository, and creates it if *allowCreateRepository* is true. It involves the following:
* Download the metadata from the S3 repo
* Stage input files for upload
* Generate repo metadata. If it never existed and *allowCreateRepository* is true, create it
* Upload staged input files and regenerated metadata files to S3
* *skipUpdate* skips regenerating the metadata
* *skipUpload* skips uploading the staged files
* *forceDeploy* overwrites files that already exist in the repository

## Example

**build.gradle**
    
    group = 'com.example'
    description = """project"""
    
    plugins {
        id 'nebula.os-package' version '2.2.6'
        id 'com.bazaarvoice.s3repo' version '1.0.4'
    }
    
    apply plugin: 'java'
    apply plugin: 'os-package-base'
    apply plugin: 'com.bazaarvoice.s3repo'
    
    
    repositories {
        ...
    }
        
    dependencies {
        ...
    }
  
    ospackage {
        packageName = description
        os = LINUX
        version = '1.2.3'
    
        ...
    }
    
    s3repo {
        s3RepositoryUrl = "s3://my-bucket-name/path/to/yum-repo/"
        inputs = buildRpm.outputs.files
        createRepoArgs = "--simple-md-filenames --no-database"
    }

    publishToS3Repo.dependsOn = buildRpm.outputs

**Command line**

    gradle publishToS3Repo