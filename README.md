# S3 Repo Gradle Plugin

This plugin is strongly based of [s3repo-maven-plugin](https://github.com/bazaarvoice/s3repo-maven-plugin), and lets you deploy resources to a remote RPM repository hosted on Amazon S3.

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