package org.nuxeo.ecm.core.storage.cloud;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("config")
public class AWSCredentialsDescriptor {

    @XNode("awsId")
    protected String awsId = "";
    
    @XNode("awsSecret")
    protected String awsSecret = "";
    
    @XNode("awsToken")
    protected String awsToken = "";
    
    @XNode("awsProfile")
    protected String awsProfile = "";

    public void merge(AWSCredentialsDescriptor contribution) {
        awsId = contribution.awsId;
        awsSecret = contribution.awsSecret;
        awsToken = contribution.awsToken;
        awsProfile = contribution.awsProfile;
        
    }

    public String getAwsId() {
        return awsId;
    }

    public String getAwsSecret() {
        return awsSecret;
    }

    public String getAwsToken() {
        return awsToken;
    }

    public String getAwsProfile() {
        return awsProfile;
    }
}
