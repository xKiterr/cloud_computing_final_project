# CN2026 Labels - Image Processing System

CN2026 Labels is a distributed image-processing system built for the Cloud Computing final project. The system accepts image files from a local Java gRPC client, stores them in Google Cloud Storage, queues processing requests through Pub/Sub, processes images on worker VMs using Vision API and Translation API, and stores the final results in Firestore.

## 1. Prerequisites and configuration

To compile and execute this project locally, the following environment is required:

- **Java Development Kit (JDK):** version 25, matching the server and worker environments.
- **Apache Maven:** used to build all modules and generate executable JAR files.
- **Google Cloud authentication:** to run the client locally, authenticate the terminal with a service account key:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/key.json"
```

The service account used by the local client and the service accounts attached to the Compute Engine VMs must have permissions appropriate for their role in the system.

Recommended roles/permissions:

- **Storage Admin** or **Storage Object Admin** - required to upload image chunks into the Cloud Storage bucket.
- **Pub/Sub Publisher** - required by the gRPC server to queue image processing requests.
- **Pub/Sub Subscriber** - required by the worker application to pull processing requests.
- **Cloud Datastore Owner / Firestore access** - required to read and write Firestore documents.
- **Compute Instance Admin / Compute Admin permissions** - required if the client is used to resize Managed Instance Groups.

For Compute Engine VMs, the instance template must also allow access to the required Google Cloud APIs. In this project the VM access scopes were set to **Allow full access to all Cloud APIs**, so that the service account IAM roles are not blocked by legacy VM access-scope restrictions.

## 2. Project structure

The repository is divided into the following modules:

- **grpcContract** - contains the `CN2526Labels.proto` contract defining the gRPC API and generating the Java stubs.
- **grpcClientApp** - interactive console application used to upload images, check results, search processed images, and resize the infrastructure.
- **grpcServerApp** - gRPC ingress server code deployed on Compute Engine.
- **labelsWorkerApp** - background image-processing application deployed on Compute Engine.
- **lookup-function** - Node.js Cloud Run Function used for dynamic discovery of active gRPC server IP addresses.

## 3. Main Google Cloud resources

The implementation uses the following main resources:

- **Project ID:** `cn2526-t2-g10`
- **Zone:** `europe-southwest1-a`
- **Cloud Storage bucket:** `cn2526-labels-bucket`
- **Pub/Sub topic:** `cn2026-image-processing`
- **Pub/Sub subscription:** `image-processing-topic-sub`
- **Firestore database:** `cn2526-t2-g10-db`
- **Firestore collection:** `image-results`
- **gRPC server Managed Instance Group:** `grpc-server-mig`
- **Worker Managed Instance Group:** `labels-app-mig`
- **Cloud Run lookup function:** `https://grpc-lookup-52998376201.europe-southwest1.run.app`

## 4. How to build

From the root directory containing the parent `pom.xml`, run:

```bash
mvn clean package
```

This builds all modules and generates executable JAR files with dependencies.

## 5. How to run the client

The deployed cloud infrastructure is expected to already exist in Google Cloud. To test the system locally, run only the client application.

From the project root, execute:

```bash
java -jar grpcClientApp/target/grpcClientApp-25-1.0-jar-with-dependencies.jar
```

The client will call the Cloud Run lookup function, display the active gRPC server IP addresses, and allow the user to choose which server to connect to.

## 6. Running server and worker JARs on VMs

The gRPC server and worker are normally started by VM startup scripts inside their respective Managed Instance Groups.

Example gRPC server startup command:

```bash
cd /var/grpcserver
export GOOGLE_APPLICATION_CREDENTIALS=key.json
java -jar /var/grpcserver/server.jar
```

Example worker startup command:

```bash
cd /var/worker
export GOOGLE_APPLICATION_CREDENTIALS=key.json
java -jar /var/worker/worker-jar-with-dependencies.jar cn2526-t2-g10 image-processing-topic-sub
```

## 7. Execution assumptions and notes

- **Dynamic IP lookup:** when the client starts, it queries the Cloud Run Function to fetch the live external IP addresses of active gRPC server VMs. An active internet connection is required.
- **Cold starts:** if the `grpc-server-mig` has been scaled to 0 instances, the lookup function will not return a usable server IP. Scale the gRPC server MIG to at least 1 instance before running the client.
- **Manual elasticity:** the client can resize both `grpc-server-mig` and `labels-app-mig` using the Compute Engine API. This requires the correct Compute Engine permissions.
- **File paths:** when submitting an image, provide the absolute path. The client removes surrounding single or double quotes, so dragging and dropping files into a macOS/Linux terminal is supported.
- **Search functionality:** search is case-insensitive and bilingual. The user can search by an English label detected by Vision API, for example `cat`, or by the Portuguese translation, for example `gato`.
- **Date format:** search dates should use the format `YYYY-MM-DD`.

## 8. Suggested testing and demonstration scenario

A recommended test scenario is:

1. Scale the gRPC server MIG to 1 VM.
2. Scale the worker MIG to 0 VMs.
3. Run the local client.
4. Submit one or more image files from the client.
5. Check the Pub/Sub subscription in the GCP Console and confirm that unprocessed messages are waiting.
6. Confirm that no result documents exist yet in the Firestore `image-results` collection for those requests.
7. Scale the worker MIG to 2 VMs.
8. Wait until the workers consume the Pub/Sub messages and process the images.
9. Check Firestore and confirm that result documents were created.
10. Use the client to retrieve results by request ID.
11. Use the search option to find images by English or Portuguese characteristics.
12. Scale the gRPC server MIG to 2 VMs and verify that the client can choose between different server IP addresses returned by the Cloud Run lookup function.

## 9. Firestore document structure

Processed results are stored in the `image-results` collection. The document ID is the `requestId` returned by the gRPC server after image submission.

Example document fields:

```text
requestId:       unique identifier returned to the client
bucketName:      Cloud Storage bucket name
blobName:        uploaded image file name in the bucket
processedDate:   timestamp of completed processing
labelsEn:        lowercase English labels detected by Vision API
labelsPt:        lowercase Portuguese translations of the labels
```

The parallel arrays `labelsEn` and `labelsPt` are used to build the result map returned by the gRPC `getProcessingResult` operation and to support bilingual search.
