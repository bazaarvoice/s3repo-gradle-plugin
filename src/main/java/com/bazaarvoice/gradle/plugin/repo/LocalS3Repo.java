package com.bazaarvoice.gradle.plugin.repo;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.bazaarvoice.gradle.plugin.util.SimpleNamespaceResolver;
import com.bazaarvoice.gradle.plugin.util.XmlUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.*;

/**
 * Created by louis.williams on 7/2/15.
 */
public class LocalS3Repo {

    private static final String YUM_REPODATA_FOLDERNAME = "repodata";
    private static final String YUM_REPODATA_METADATA = "repomd.xml";
    private static final String[] YUM_REPODATA_FILE_TYPES = {"primary", "filelists", "other"};

    private static final String CREATE_REPO_COMMAND = "createrepo -p ";

    private static Logger log = Logging.getLogger(LocalS3Repo.class.getName());

    private AmazonS3Client s3Client;
    private S3RepositoryPath s3RepositoryPath;
    private String stagingDirectory;
    private boolean allowCreateRepo;
    private List<File> synthesizedFiles;

    public LocalS3Repo(AmazonS3Client s3Client, S3RepositoryPath s3RepositoryPath, String stagingDirectory, boolean allowCreate) {
        this.s3Client = s3Client;
        this.s3RepositoryPath = s3RepositoryPath;
        this.stagingDirectory = stagingDirectory;
        this.allowCreateRepo = allowCreate;
    }

    public void verifyRepo() throws Exception {

        cleanUpStage();

        try {
            /* Download the repository metadata to a local staging directory */
            downloadMetadataToStaging();

            /* We want to add files that we will end re-uploading here */
            synthesizedFiles = new ArrayList<File>();

            /* Get the metadata file from staging */
            File metaData = getRepoMetadataFile();
            log.info("Metadata file: " + metaData);

            /* If the repo doesn't exist, don't do anything for now */
            if (metaData.isFile()) {

                synthesizeRepositoryFiles(metaData);
                verifyChecksums();

            } else if (!allowCreateRepo) {

                String message = "Repo metadata '" + YUM_REPODATA_METADATA + "' file not found. If you want to create the repo, set allowCreateRepo to true";
                log.error(message);

                throw new RuntimeException(message);
            }


        } catch (Exception e) {

            /* Don't forget to clean up if something goes wrong...*/
//            cleanUpStage();
            throw e;
        }

        /* Delete staging directory */
//        cleanUpStage();

    }

    public void uploadToRepo(boolean noUpload) throws IOException {

        cleanSynthesizedFiles();

        /* Do upload to S3 */
        Collection<File> stagedFiles = FileUtils.listFiles(new File(stagingDirectory), null, true);

        for (File file : stagedFiles) {
            String bucketKey = getBucketKeyPath(file);

            PutObjectRequest request = new PutObjectRequest(s3RepositoryPath.getBucketName(), bucketKey, file);

            log.info("Uploading  " + file + " => s3://" + s3RepositoryPath.getBucketName() + "/" + bucketKey);

            if (!noUpload) {
                s3Client.putObject(request);
            } else {
                log.info("  Skipping");
            }
        }

    }

    public String getBucketKeyPath(File file) throws IOException {

        /* Remove the canonical part of the path, and then the filename to get just the folder key */
        String stagingPath = new File(stagingDirectory).getCanonicalPath();
        String fullKey = file.getCanonicalPath().replace(stagingPath, "");
        if (fullKey.startsWith("/")) {
            fullKey = fullKey.substring(1);
        }
        return fullKey;
    }

    public void updateOrCreateRemoteRepo(String createRepoArgs) throws IOException {
        if (!getRepoMetadataFile().isFile()) {
            if (allowCreateRepo) {
                updateRemoteRepo(createRepoArgs, true);
            } else {
                throw new RuntimeException("Repo doesn't exist and allowCreateRepo not set to true");
            }
        } else {
            updateRemoteRepo(createRepoArgs, false);
        }
    }

    private void updateRemoteRepo(String createRepoArgs, boolean allowCreate) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        String command = CREATE_REPO_COMMAND;

        String inputDirectory = stagingDirectory + "/" + s3RepositoryPath.getFolderPath();

        if (!allowCreate) {
            command += "--update --skip-stat ";
        }
        if (createRepoArgs != null) {
            command += createRepoArgs + " ";
        }
        command += inputDirectory;

        log.info("Executing \'" + command + "\'");
        try {

            Process process = runtime.exec(command);
            process.waitFor();

            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = stdOut.readLine()) != null) {
                log.info(line);
            }
            while ((line = stdErr.readLine()) != null) {
                log.info(line);
            }

            int result = process.exitValue();

            if (result != 0) {
                throw new RuntimeException(command + " returned: " + result);
            }

        } catch (Exception e) {
            throw new RuntimeException("Unable to execute: " + command, e);
        }
        log.info("Successfully built repo using directory: " + stagingDirectory);
    }



    public void stageInputFiles(FileCollection files, boolean force) throws IOException {
        for (File inputFile : files) {
            File destination = new File(stagingDirectory + "/" + s3RepositoryPath.getFolderPath(), inputFile.getName()).getCanonicalFile();

            log.info("Copying " + inputFile + " => " + destination);

            /* If we try to stage a file that already exist, and the force option is true, remove it from the synthesized files so we upload it later*/
            if (destination.isFile()) {
                log.info("File exists in repo already: " + destination);
                if (force) {
                    log.info("  Overwriting...");
                } else {
                    throw new IOException("Attempting to deploy file '" + destination.getName() + "' that exists in repo, and forceDeploy is not set to true");
                }
            }
            synthesizedFiles.remove(destination);

            FileUtils.copyFile(inputFile, destination);
        }
    }

    private void downloadMetadataToStaging() {


        String metadataFolderPath = getRepodataPath();

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(s3RepositoryPath.getBucketName())
                .withPrefix(metadataFolderPath);

        List<S3ObjectSummary> results = listAllObjects(s3Client, listObjectsRequest);


        log.info("Found " + results.size() + " objects in bucket '" + s3RepositoryPath.getBucketName()
                + "' with prefix '" + metadataFolderPath + "'...");

        for (S3ObjectSummary summary : results) {
            final String relativePath = summary.getKey();

            if (summary.getKey().endsWith("/")) {
                log.info("Downloading: " + s3RepositoryPath + "/" + relativePath + " => (skipping; it's a folder)");
                continue;
            }

            S3Object object = s3Client.getObject(new GetObjectRequest(s3RepositoryPath.getBucketName(), summary.getKey()));

            try {
                File targetFile = new File(stagingDirectory, relativePath);
                log.info("Downloading: " + s3RepositoryPath.getBucketName() + "/" + relativePath + " => " + targetFile);
                FileUtils.touch(targetFile);

                final S3ObjectInputStream objectContent = object.getObjectContent();

                objectContent.getHttpRequest();

                FileOutputStream targetFileStream = FileUtils.openOutputStream(targetFile);

                IOUtils.copy(objectContent, targetFileStream);


            } catch (IOException e) {
                throw new RuntimeException("failed to download object from s3: " + summary.getKey(), e);
            }
        }
    }

    private File getRepoMetadataFile() {
        return  new File(stagingDirectory + "/" + getRepodataPath(), YUM_REPODATA_METADATA);

    }

    private void cleanSynthesizedFiles() throws IOException {
        for (File synthesized : synthesizedFiles) {
            log.info("Removing synthesized " + synthesized + " before upload to repo");
            if (!synthesized.delete()) {
                throw new IOException("Failed to delete file " + synthesized);
            }
        }
    }

    private void synthesizeRepositoryFiles(File metadataFile) throws Exception {

        /* Parse repomd.xml file */
        Document repomdXml = XmlUtils.parseXmlFile(metadataFile);

        /* Parse primary.xml */
        Document primaryXml = XmlUtils.parseXmlFile(resolveMetadataFile("primary", repomdXml));

        /* Get file list */
        List<String> localFiles = parseFileListFromPrimaryMetadata(primaryXml);

        log.info("Files (" + localFiles.size() + ")");

        ListObjectsRequest request = new ListObjectsRequest()
                .withBucketName(s3RepositoryPath.getBucketName());
        if (!s3RepositoryPath.getFolderPath().isEmpty()) {
            request.withPrefix(s3RepositoryPath.getFolderPath() + "/");
        }

        List<S3ObjectSummary> result = listAllObjects(s3Client, request);

        // we will start with a set of metadata-declared files and remove any file we find that exists in the repo;
        // we expect the Set to be empty when finished iteration. note that s3 api returns bucket-relative
        // paths, so we prefix each of our repoRelativeFilePaths with the repository path.

        Set<String> bucketRelativePaths = new HashSet<String>();
        for (String repoRelativeFilePath : localFiles) {
            if (!s3RepositoryPath.getFolderPath().isEmpty()) {
                bucketRelativePaths.add(s3RepositoryPath.getFolderPath() + "/" + repoRelativeFilePath);
            } else {
                bucketRelativePaths.add(repoRelativeFilePath);
            }
        }

        // for each bucket relative path in the listObjects result, remove from our set
        for (S3ObjectSummary summary : result) {
            bucketRelativePaths.remove(summary.getKey());
        }

        // now, expect set to be empty
        if (!bucketRelativePaths.isEmpty()) {
            throw new RuntimeException("Primary metadata file declared files that did not exist in the repository: " + bucketRelativePaths);
        }

        // for each file in our repoRelativeFilePathList, touch/synthesize the file
        for (String repoRelativeFilePath : localFiles) {
            File file = new File(stagingDirectory + "/" + s3RepositoryPath.getFolderPath(), repoRelativeFilePath).getCanonicalFile();
            if (file.exists()) {
                throw new RuntimeException("Repo already has this file: " + file.getPath());
            }
            FileUtils.touch(file);
            synthesizedFiles.add(file);
            log.info("Synthesizing " + file);
        }
    }

    private List<String> parseFileListFromPrimaryMetadata(Document primaryMetadata) throws IOException {

        /* Get root namespace */
        String namespaceUri = determineRootNamespaceUri(primaryMetadata);

        List<String> fileList = new ArrayList<>();

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(SimpleNamespaceResolver.forPrefixAndNamespace("common", namespaceUri));
        NodeList repoRelativeFilePaths =
                (NodeList) evaluateXPathNodeSet(xpath, "//common:metadata/common:package/common:location/@href", primaryMetadata);
        for (int i = 0; i < repoRelativeFilePaths.getLength(); ++i) {
            fileList.add(repoRelativeFilePaths.item(i).getNodeValue());
        }

        return fileList;
    }

    private File resolveMetadataFile(String type, Document metadata) throws IOException {

        // determine root namespace for use in xpath queries
        String rootNamespaceUri = determineRootNamespaceUri(metadata);
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(SimpleNamespaceResolver.forPrefixAndNamespace("repo", rootNamespaceUri));

        // metadata file, relative to *repository* root
        String repoRelativeMetadataFilePath =
                evaluateXPathString(xpath, "//repo:repomd/repo:data[@type='" + type + "']/repo:location/@href", metadata);

        // determine metadata file (e.g., "repodata/primary.xml.gz")
        File metadataFile = new File(stagingDirectory + "/" + s3RepositoryPath.getFolderPath(), repoRelativeMetadataFilePath);
        if (!metadataFile.isFile() || !metadataFile.getName().endsWith(".gz")) {
            throw new IOException(type + " metadata file, '" + metadataFile.getPath() +
                    "', does not exist or does not have .gz extension");
        }
        return metadataFile;
    }

    private static String evaluateXPathString(XPath xpath, String expression, Document document) throws IOException {
        try {
            return xpath.evaluate(expression, document);
        } catch (XPathExpressionException e) {
            throw new IOException(expression, e);
        }
    }

    private void cleanUpStage() {
        try {
            log.info("Deleting directory " + stagingDirectory);
            if (new File(stagingDirectory).exists()) {
                FileUtils.deleteDirectory(new File(stagingDirectory));
            } else {
                log.info("Directory doesn't exist");
            }
        } catch (IOException e) {
            log.error("Could not clean staging directory: " + e.getMessage());
        }
    }

    private void verifyChecksums() throws IOException {
        File repoMetadataFile = getRepoMetadataFile();
        if (!repoMetadataFile.isFile()) {
            throw new IllegalStateException("File didn't exist: " + repoMetadataFile.getPath());
        }
        Document repoMetadata = XmlUtils.parseXmlFile(repoMetadataFile);

        log.info("Verifying checksums...");

        // check checksum of repo files
        for (String fileType : YUM_REPODATA_FILE_TYPES) {
            final File file = resolveMetadataFile(fileType, repoMetadata);
            try {
                final FileInputStream fileIn = new FileInputStream(file);
                try {
                    final Checksum checksum = resolveMetadataChecksum(fileType, repoMetadata);
                    String digest;
                    if ("sha".equals(checksum.checksumType) || "sha1".equals(checksum.checksumType)) {
                        digest = DigestUtils.shaHex(fileIn);
                    } else if ("sha256".equals(checksum.checksumType)) {
                        digest = DigestUtils.sha256Hex(fileIn);
                    } else if ("sha384".equals(checksum.checksumType)) {
                        digest = DigestUtils.sha384Hex(fileIn);
                    } else if ("sha512".equals(checksum.checksumType)) {
                        digest = DigestUtils.sha512Hex(fileIn);
                    } else if ("md5".equals(checksum.checksumType)) {
                        digest = DigestUtils.md5Hex(fileIn);
                    } else {
                        // default to sha256
                        digest = DigestUtils.sha256Hex(fileIn);
                    }
                    if (!checksum.checksumValue.equals(digest)) {
                        throw new RuntimeException("Checksum does not match for " + file.getPath() + ". Expected " + checksum.checksumValue + " but got " + digest);
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Unable to calculate checksum for " + file.getPath(), e);
                } finally {
                    fileIn.close();
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Repo file " + file.getPath() + " not found");
            }
        }
        log.info("Checksum verification passed.");

    }

    private Checksum resolveMetadataChecksum(String type, Document metadata) throws IOException {
        // determine root namespace for use in xpath queries
        String rootNamespaceUri = determineRootNamespaceUri(metadata);
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(SimpleNamespaceResolver.forPrefixAndNamespace("repo", rootNamespaceUri));
        return new Checksum(
                evaluateXPathString(xpath, "//repo:repomd/repo:data[@type='" + type + "']/repo:checksum/@type", metadata),
                evaluateXPathString(xpath, "//repo:repomd/repo:data[@type='" + type + "']/repo:checksum", metadata)
        );
    }

    private String getRepodataPath() {
        String metadataFolderPath = YUM_REPODATA_FOLDERNAME + "/";

        if (s3RepositoryPath.getFolderPath() != null) {
            metadataFolderPath = s3RepositoryPath.getFolderPath() + "/" + metadataFolderPath;
        }

        return metadataFolderPath;
    }

    private static String determineRootNamespaceUri(Document metadata) {
        return metadata.getChildNodes().item(0).getNamespaceURI();
    }

    private static Object evaluateXPathNodeSet(XPath xpath, String expression, Document document) throws IOException {
        try {
            return xpath.evaluate(expression, document, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IOException(expression, e);
        }
    }

    private static List<S3ObjectSummary> listAllObjects(AmazonS3Client s3Client, ListObjectsRequest request) {
        List<S3ObjectSummary> results = new ArrayList<>();

        ObjectListing result = s3Client.listObjects(request);

        results.addAll(result.getObjectSummaries());

        while (result.isTruncated()) {
            result = s3Client.listNextBatchOfObjects(result);
            results.addAll(result.getObjectSummaries());
        }

        return results;
    }

    private static class Checksum {
        private final String checksumType;
        private final String checksumValue;

        private Checksum(final String checksumType, final String checksumValue) {
            this.checksumType = checksumType;
            this.checksumValue = checksumValue;
        }
    }
}
