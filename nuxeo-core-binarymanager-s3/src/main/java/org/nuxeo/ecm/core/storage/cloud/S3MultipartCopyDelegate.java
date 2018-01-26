package org.nuxeo.ecm.core.storage.cloud;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyPartRequest;
import com.amazonaws.services.s3.model.CopyPartResult;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;

public class S3MultipartCopyDelegate {

    // Helper function that constructs ETags.
    static List<PartETag> GetETags(List<CopyPartResult> responses) {
        List<PartETag> etags = new ArrayList<PartETag>();
        for (CopyPartResult response : responses) {
            etags.add(new PartETag(response.getPartNumber(), response.getETag()));
        }
        return etags;
    }

    private AmazonS3 s3Client;

    // Default part is 5MB
    private long partSize = 5 * (long) Math.pow(2.0, 20.0);

    public S3MultipartCopyDelegate(AmazonS3 s3Client, long partSize) {
        this.s3Client = s3Client;
        this.partSize = partSize;
    }

    public S3MultipartCopyDelegate(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Copies files using multi-part from one bucket to another. Returns the complete result or throws an SdkClientException exception
     *
     * @param request
     * @return
     */
    public CompleteMultipartUploadResult copy(CopyObjectRequest request) {
        return copy(request.getSourceBucketName(), request.getSourceKey(), request.getDestinationBucketName(),
                request.getDestinationKey(), request.getNewObjectMetadata());
    }

    /**
     * Copies files using multi-part from one bucket to another. Returns the complete result or throws an SdkClientException exception
     *
     * @param sourceBucketName
     * @param sourceObjectKey
     * @param targetBucketName
     * @param targetObjectKey
     * @return
     */
    public CompleteMultipartUploadResult copy(String sourceBucketName, String sourceObjectKey, String targetBucketName,
            String targetObjectKey, ObjectMetadata metadata) {

        // List to store copy part responses.

        List<CopyPartResult> copyResponses = new ArrayList<CopyPartResult>();

        InitiateMultipartUploadRequest initiateRequest = new InitiateMultipartUploadRequest(targetBucketName,
                targetObjectKey);

        InitiateMultipartUploadResult initResult = s3Client.initiateMultipartUpload(initiateRequest);

        // Get object size.
        if (metadata == null) {
            GetObjectMetadataRequest metadataRequest = new GetObjectMetadataRequest(sourceBucketName, sourceObjectKey);
            metadata = s3Client.getObjectMetadata(metadataRequest);
        }

        long objectSize = metadata.getContentLength(); // in bytes

        long bytePosition = 0;
        for (int i = 1; bytePosition < objectSize; i++) {
            CopyPartRequest copyRequest = new CopyPartRequest().withDestinationBucketName(targetBucketName)
                                                               .withDestinationKey(targetObjectKey)
                                                               .withSourceBucketName(sourceBucketName)
                                                               .withSourceKey(sourceObjectKey)
                                                               .withUploadId(initResult.getUploadId())
                                                               .withFirstByte(bytePosition)
                                                               .withLastByte(bytePosition + partSize - 1 >= objectSize
                                                                       ? objectSize - 1
                                                                       : bytePosition + partSize - 1)
                                                               .withPartNumber(i);

            copyResponses.add(s3Client.copyPart(copyRequest));
            bytePosition += partSize;

        }
        List<PartETag> getETags = GetETags(copyResponses);
        CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(targetBucketName,
                targetObjectKey, initResult.getUploadId(), getETags);

        try {
            CompleteMultipartUploadResult completeUploadResponse = s3Client.completeMultipartUpload(completeRequest);
            return completeUploadResponse;
        } catch (SdkClientException e) {
            s3Client.abortMultipartUpload(
                    new AbortMultipartUploadRequest(sourceBucketName, sourceObjectKey, initResult.getUploadId()));
            throw e;
        }
    }

}
