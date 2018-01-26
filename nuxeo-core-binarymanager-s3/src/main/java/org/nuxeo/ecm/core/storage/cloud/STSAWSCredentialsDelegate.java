package org.nuxeo.ecm.core.storage.cloud;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.model.ComponentInstance;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class STSAWSCredentialsDelegate extends AbstractAWSCredentialsDelegate implements AWSCredentialsDelegate{
    private static final Log log = LogFactory.getLog(STSAWSCredentialsDelegate.class);
    
    private String profileName = null;
    
    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        super.registerContribution(contribution, extensionPoint, contributor);
        profileName = descriptor.getAwsProfile();
        
        if (isBlank(profileName)) {
            profileName = "default";
        }
    }

    public AWSCredentialsProvider getCredentials() {
        try {
            AWSCredentialsProvider provider = new ProfileCredentialsProvider(profileName);
            provider.getCredentials();
            return provider;
        } catch (AmazonClientException e) {
            throw new RuntimeException("Missing AWS credentials", e);
        }
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }
}
