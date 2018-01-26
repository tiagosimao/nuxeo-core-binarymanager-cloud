package org.nuxeo.ecm.core.storage.cloud;

import com.amazonaws.auth.AWSCredentialsProvider;

public interface AWSCredentialsDelegate {

    AWSCredentialsProvider getCredentials();

}