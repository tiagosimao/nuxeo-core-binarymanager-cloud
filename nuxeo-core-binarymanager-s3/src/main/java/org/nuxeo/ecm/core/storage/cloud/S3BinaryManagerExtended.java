package org.nuxeo.ecm.core.storage.cloud;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.storage.sql.S3BinaryManager;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Builder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CryptoConfiguration;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.base.MoreObjects;

public class S3BinaryManagerExtended extends S3BinaryManager {

    public static final String IS_LOCAL = "isLocal";

    private static final Log log = LogFactory.getLog(S3BinaryManagerExtended.class);

    protected boolean isLocalS3 = false;
    
    private AWSCredentialsDelegate awsCredentialsDelegate;
    
    @Override
    protected void abortOldUploads() throws IOException {
        setupCloudClient();
        super.abortOldUploads();
    }
     
    
    @Override
    protected void setupCloudClient() throws IOException {
        
        awsCredentialsDelegate = Framework.getService(AWSCredentialsDelegate.class);
        
        if (awsCredentialsDelegate == null) {return;}
        
        AmazonS3Builder<?, ?> s3Builder = setupBuilder();

        amazonS3 = (AmazonS3) s3Builder.build();

        // Try to create bucket if it doesn't exist
        try {
            if (!amazonS3.doesBucketExist(bucketName)) {
                amazonS3.createBucket(bucketName);

                if(!isLocalS3) {
                    amazonS3.setBucketAcl(bucketName, CannedAccessControlList.Private);
                }
            }
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }

        // compat for NXP-17895, using "downloadfroms3", to be removed
        // these two fields have already been initialized by the base class initialize()
        // using standard property "directdownload"
        String dd = getProperty(DIRECTDOWNLOAD_PROPERTY_COMPAT);
        if (dd != null) {
            directDownload = Boolean.parseBoolean(dd);
        }
        int dde = getIntProperty(DIRECTDOWNLOAD_EXPIRE_PROPERTY_COMPAT);
        if (dde >= 0) {
            directDownloadExpire = dde;
        }

        transferManager = buildTransfertManager();
        abortOldUploads();
    }

    private TransferManager buildTransfertManager() {
        return TransferManagerBuilder.standard().withS3Client(amazonS3).build();
    }

    private AmazonS3Builder<?, ?> setupBuilder() {
        // Get settings from the configuration
        bucketName = getProperty(BUCKET_NAME_PROPERTY);
        bucketNamePrefix = MoreObjects.firstNonNull(getProperty(BUCKET_PREFIX_PROPERTY), StringUtils.EMPTY);
        String bucketRegion = getProperty(BUCKET_REGION_PROPERTY);
        if (isBlank(bucketRegion)) {
            bucketRegion = DEFAULT_BUCKET_REGION;
        }
        String awsID = getProperty(AWS_ID_PROPERTY);
        String awsSecret = getProperty(AWS_SECRET_PROPERTY);

        String proxyHost = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_HOST);
        String proxyPort = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_PORT);
        String proxyLogin = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_LOGIN);
        String proxyPassword = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_PASSWORD);

        int maxConnections = getIntProperty(CONNECTION_MAX_PROPERTY);
        int maxErrorRetry = getIntProperty(CONNECTION_RETRY_PROPERTY);
        int connectionTimeout = getIntProperty(CONNECTION_TIMEOUT_PROPERTY);
        int socketTimeout = getIntProperty(SOCKET_TIMEOUT_PROPERTY);

        String keystoreFile = getProperty(KEYSTORE_FILE_PROPERTY);
        String keystorePass = getProperty(KEYSTORE_PASS_PROPERTY);
        String privkeyAlias = getProperty(PRIVKEY_ALIAS_PROPERTY);
        String privkeyPass = getProperty(PRIVKEY_PASS_PROPERTY);
        String endpoint = getProperty(ENDPOINT_PROPERTY);
        String sseprop = getProperty(SERVERSIDE_ENCRYPTION_PROPERTY);

        String isLocal = getProperty(IS_LOCAL);

        if (isNotBlank(sseprop)) {
            userServerSideEncryption = Boolean.parseBoolean(sseprop);
        }

        // Fallback on default env keys for ID and secret
        if (isBlank(awsID)) {
            awsID = System.getenv(AWS_ID_ENV);
        }
        if (isBlank(awsSecret)) {
            awsSecret = System.getenv(AWS_SECRET_ENV);
        }

        if (isBlank(bucketName)) {
            throw new RuntimeException("Missing conf: " + BUCKET_NAME_PROPERTY);
        }

        if (!isBlank(bucketNamePrefix) && !bucketNamePrefix.endsWith("/")) {
            log.warn(String.format("%s %s S3 bucket prefix should end by '/' " + ": added automatically.",
                    BUCKET_PREFIX_PROPERTY, bucketNamePrefix));
            bucketNamePrefix += "/";
        }
        
        awsCredentialsProvider = awsCredentialsDelegate.getCredentials();

        // set up client configuration
        clientConfiguration = new ClientConfiguration();
        if (isNotBlank(proxyHost)) {
            clientConfiguration.setProxyHost(proxyHost);
        }
        if (isNotBlank(proxyPort)) {
            clientConfiguration.setProxyPort(Integer.parseInt(proxyPort));
        }
        if (isNotBlank(proxyLogin)) {
            clientConfiguration.setProxyUsername(proxyLogin);
        }
        if (proxyPassword != null) { // could be blank
            clientConfiguration.setProxyPassword(proxyPassword);
        }
        if (maxConnections > 0) {
            clientConfiguration.setMaxConnections(maxConnections);
        }
        if (maxErrorRetry >= 0) { // 0 is allowed
            clientConfiguration.setMaxErrorRetry(maxErrorRetry);
        }
        if (connectionTimeout >= 0) { // 0 is allowed
            clientConfiguration.setConnectionTimeout(connectionTimeout);
        }
        if (socketTimeout >= 0) { // 0 is allowed
            clientConfiguration.setSocketTimeout(socketTimeout);
        }

        // set up encryption
        encryptionMaterials = null;
        if (isNotBlank(keystoreFile)) {
            boolean confok = true;
            if (keystorePass == null) { // could be blank
                log.error("Keystore password missing");
                confok = false;
            }
            if (isBlank(privkeyAlias)) {
                log.error("Key alias missing");
                confok = false;
            }
            if (privkeyPass == null) { // could be blank
                log.error("Key password missing");
                confok = false;
            }
            if (!confok) {
                throw new RuntimeException("S3 Crypto configuration incomplete");
            }
            try {
                // Open keystore
                File ksFile = new File(keystoreFile);
                FileInputStream ksStream = new FileInputStream(ksFile);
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(ksStream, keystorePass.toCharArray());
                ksStream.close();
                // Get keypair for alias
                if (!keystore.isKeyEntry(privkeyAlias)) {
                    throw new RuntimeException("Alias " + privkeyAlias + " is missing or not a key alias");
                }
                PrivateKey privKey = (PrivateKey) keystore.getKey(privkeyAlias, privkeyPass.toCharArray());
                Certificate cert = keystore.getCertificate(privkeyAlias);
                PublicKey pubKey = cert.getPublicKey();
                KeyPair keypair = new KeyPair(pubKey, privKey);
                // Get encryptionMaterials from keypair
                encryptionMaterials = new EncryptionMaterials(keypair);
                cryptoConfiguration = new CryptoConfiguration();
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException("Could not read keystore: " + keystoreFile + ", alias: " + privkeyAlias, e);
            }
        }
        isEncrypted = encryptionMaterials != null;


        if (isNotBlank(isLocal)) {
            isLocalS3 = Boolean.parseBoolean(isLocal);
        }

        AmazonS3Builder<?, ?> s3Builder;
        if (!isEncrypted) {
            s3Builder = AmazonS3ClientBuilder.standard()
                            .withCredentials(awsCredentialsProvider)
                            .withClientConfiguration(clientConfiguration);

        } else {
            s3Builder = AmazonS3EncryptionClientBuilder.standard()
                            .withClientConfiguration(clientConfiguration)
                            .withCryptoConfiguration(cryptoConfiguration)
                            .withCredentials(awsCredentialsProvider)
                            .withEncryptionMaterials(new StaticEncryptionMaterialsProvider(encryptionMaterials));
        }
        
        if (isNotBlank(endpoint)) {
            s3Builder = s3Builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, bucketRegion));
        } else {
            s3Builder = s3Builder.withRegion(bucketRegion);
        }

        if(isLocalS3) {
            // Applying default S3V4 API style
            // TODO expose property
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setSignerOverride("AWSS3V4SignerType");
            s3Builder.withClientConfiguration(clientConfiguration);

            s3Builder.withPathStyleAccessEnabled(true);
            s3Builder.withChunkedEncodingDisabled(true);
        }
        return s3Builder;
    }

    public void setAwsCredentialsDelegate(AWSCredentialsDelegate awsCredentialsDelegate) {
        this.awsCredentialsDelegate = awsCredentialsDelegate;
    }


}
