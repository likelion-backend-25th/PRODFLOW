package net.likelion.bebc25.prodflow.product;

import java.io.ByteArrayInputStream;
import java.util.Map;

import net.likelion.bebc25.prodflow.common.storage.S3Properties;
import net.likelion.bebc25.prodflow.common.storage.S3StorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3StorageClientTest {
    @Test
    void uploadsAndReadsPdfWithConfirmationMetadata() throws Exception {
        var s3 = mock(S3Client.class);
        var storage = new S3StorageClient(s3, mock(S3Presigner.class),
                new S3Properties("ap-northeast-2", new S3Properties.S3("test-bucket")));
        byte[] pdf = new byte[]{1, 2, 3};
        Map<String, String> metadata = Map.of("confirmation-token", "test-token");
        storage.upload("quotations/test.pdf", new ByteArrayInputStream(pdf), "application/pdf", pdf.length, metadata);
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().metadata()).isEqualTo(metadata);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).containsExactly(pdf);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().metadata(metadata).build(), pdf));
        var file = storage.read("quotations/test.pdf");
        assertThat(file.content()).containsExactly(pdf);
        assertThat(file.metadata()).isEqualTo(metadata);
    }
}
