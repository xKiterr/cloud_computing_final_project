CN2026 Labels - Image Processing System
#### 1. Prerequisites & Configurations
To compile and execute this project locally, the following environment configurations are required:
* **Java Development Kit (JDK):** Version 25 (Required to match the server/worker environments).
* **Apache Maven:** For building the project and managing dependencies.
* **GCP Authentication:** To run the Client application locally, your terminal must be authenticated with Google Cloud using a Service Account key:
  `export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/key.json"`

  **Required IAM Roles:** Ensure the Service Account utilizing this key (and the Service Account attached to the Compute Engine VMs) is granted the following permissions:
    * `Storage Admin` (to pipe image chunks into the bucket)
    * `Pub/Sub Publisher` (to queue processing requests)
    * `Pub/Sub Subscriber` (for the background workers to pull requests)
    * `Cloud Datastore Owner` (to read and write NoSQL documents to Firestore)

2. Project Structure
   The repository is divided into the following modules:

grpcContract: Contains the .proto file defining the gRPC API and generates the Java stubs.

grpcClientApp: The interactive console application used to upload images, check results, and scale the infrastructure.

grpcServerApp: The ingress server code (deployed on Compute Engine).

labelsWorkerApp: The background processor code (deployed on Compute Engine).

lookup-function: The Node.js code deployed to Cloud Run Functions for dynamic IP discovery.

3. How to Build
   To compile the entire project and generate the executable .jar files with all dependencies included, navigate to the root directory containing the parent pom.xml and run:

mvn clean package

4. How to Run the Client (Testing)
   To test the system, you only need to run the Client application locally. The Cloud infrastructure (Servers, Workers, Pub/Sub, Storage) is already deployed and hosted on Google Cloud.

Navigate to the client module and execute the compiled jar:

java -jar grpcClientApp/target/grpcClientApp-25-1.0-jar-with-dependencies.jar

5. Execution Assumptions & Notes
   Dynamic IP Lookup: Upon startup, the client will pause for a moment to query the Cloud Run Function. It requires an active internet connection to fetch the live IPs of the gRPC servers.

Cold Starts: If the Managed Instance Groups (MIGs) have been scaled to 0 instances to save costs, the IP lookup will fail. Use the GCP Console to scale the grpc-server-mig to at least 1 instance before running the client, or use Option 4 in the client menu if you manually connect to a known IP.

File Paths: When submitting an image, provide the absolute path. The client is programmed to automatically strip single quotes (') from the path, so dragging and dropping files directly into the macOS/Linux terminal is fully supported.

Search Functionality: The characteristic search is case-insensitive and bilingual. You can search using either the detected English label (e.g., "cat") or the Portuguese translation (e.g., "gato").