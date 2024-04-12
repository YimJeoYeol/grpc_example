package com.example.grpc;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;

import com.google.protobuf.ByteString;
import java.io.File;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import javax.sound.sampled.*;
import java.util.concurrent.CountDownLatch;
import com.nbp.cdncp.nest.grpc.proto.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

public class GRpcClient {
    public static void main(String[] args) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("clovaspeech-gw.ncloud.com:50051")
                .useTransportSecurity()
                .build();
        NestServiceGrpc.NestServiceStub client = NestServiceGrpc.newStub(channel);
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer ${secretKey}");
        client = MetadataUtils.attachHeaders(client, metadata);

        StreamObserver<NestResponse> responseObserver = new StreamObserver<NestResponse>() {
            @Override
            public void onNext(NestResponse response) {
                System.out.println("Received response: " + response.getContents());
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException error = (StatusRuntimeException) t;
                    System.out.println(error.getStatus().getDescription());
                }
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("completed");
                latch.countDown();
            }
        };

        StreamObserver<NestRequest> requestObserver = client.recognize(responseObserver);

        // 마이크로부터 오디오 데이터 캡처를 시작
        captureAudioFromMicrophone(requestObserver);

        latch.await();
        channel.shutdown();
    }

    private static void captureAudioFromMicrophone(StreamObserver<NestRequest> requestObserver) {
        try {
            final AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Line not supported");
                System.exit(0);
            }

            final TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();

            // CONFIG 메시지 전송
            requestObserver.onNext(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder()
                            .setConfig("{\"transcription\":{\"language\":\"ko\"}}")
                            .build())
                    .build());

            byte[] buffer = new byte[32000]; // 버퍼 크기 조정이 필요할 수 있음
            int bytesRead;

            System.out.println("Starting audio capture from microphone...");
            while (true) {
                bytesRead = targetLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    requestObserver.onNext(NestRequest.newBuilder()
                            .setType(RequestType.DATA)
                            .setData(NestData.newBuilder()
                                    .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                                    .setExtraContents("{ \"seqId\": 0, \"epFlag\": false}")
                                    .build())
                            .build());
                }
            }
        } catch (LineUnavailableException e) {
            System.out.println("LineUnavailableException: " + e.getMessage());
        }
    }
}

/*
* requestObserver.onNext(NestRequest.newBuilder()
			.setType(RequestType.CONFIG)
			.setConfig(NestConfig.newBuilder()
				.setConfig("{\"transcription\":{\"language\":\"ko\"}}")
				.build())
			.build());

		java.io.File file = new java.io.File("~/media/42s.wav");
		byte[] buffer = new byte[32000];
		int bytesRead;
		FileInputStream inputStream = new FileInputStream(file);
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			requestObserver.onNext(NestRequest.newBuilder()
				.setType(RequestType.DATA)
				.setData(NestData.newBuilder()
					.setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
					.setExtraContents("{ \"seqId\": 0, \"epFlag\": false}")
					.build())
				.build());
		}
		requestObserver.onCompleted();
		latch.await();
		channel.shutdown();
	}

}
* */