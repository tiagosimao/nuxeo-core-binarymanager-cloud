package org.nuxeo.ecm.core.storage.cloud;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.storage.sql.BasicAWSCredentialsProvider;
import org.nuxeo.runtime.model.ComponentInstance;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;

public class BasicCredentialsDelegate extends AbstractAWSCredentialsDelegate implements AWSCredentialsDelegate {
    private static final Log log = LogFactory.getLog(BasicCredentialsDelegate.class);
    
    private String awsID;
    private String awsSecret;
    
    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        super.registerContribution(contribution, extensionPoint, contributor);
        awsID = descriptor.getAwsId();
        
        if (StringUtils.isBlank(awsID)) {
            awsID = "default";
        }
        awsSecret = descriptor.getAwsSecret();
    }

    /* (non-Javadoc)
     * @see org.nuxeo.ecm.core.storage.cloud.AWSCredentialsDelegate#getCredentials(java.lang.String, java.lang.String)
     */
    @Override
    public AWSCredentialsProvider getCredentials() {
        // Set Up credentials
        if (StringUtils.isBlank(awsID) || StringUtils.isBlank(awsSecret)) {
            AWSCredentialsProvider awsCredentialsProvider = InstanceProfileCredentialsProvider.getInstance();
            try {
                awsCredentialsProvider.getCredentials();
                return awsCredentialsProvider;
            } catch (AmazonClientException e) {
                throw new RuntimeException("Missing AWS credentials and no instance role found", e);
            }
        } else {
            return new BasicAWSCredentialsProvider(awsID, awsSecret);
        }
    }
}
