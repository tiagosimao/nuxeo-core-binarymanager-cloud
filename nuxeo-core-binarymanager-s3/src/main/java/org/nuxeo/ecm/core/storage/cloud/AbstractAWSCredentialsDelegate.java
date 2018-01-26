package org.nuxeo.ecm.core.storage.cloud;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;


public class AbstractAWSCredentialsDelegate extends DefaultComponent {

    protected static final String XP = "configuration";
    protected AWSCredentialsDescriptor descriptor;

    public AbstractAWSCredentialsDelegate() {
        super();
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP.equals(extensionPoint)) {
            if (contribution instanceof AWSCredentialsDescriptor) {
                if (descriptor != null) {
                    descriptor.merge((AWSCredentialsDescriptor) contribution);
                } else {
                    descriptor = (AWSCredentialsDescriptor) contribution;
                }
    
            } else {
                throw new NuxeoException("Invalid descriptor: " + contribution.getClass());
            }
        } else {
            throw new NuxeoException("Invalid extension point: " + extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP.equals(extensionPoint)) {
            if (contribution instanceof AWSCredentialsDescriptor) {
                descriptor = null;
            }
        }
    }

}