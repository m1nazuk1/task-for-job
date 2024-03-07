package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class CrptApi {

    private final ScheduledExecutorService scheduler;
    private final Semaphore permits;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.permits = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

        long period = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(() -> permits.release(requestLimit - permits.availablePermits()), 0, period, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException, ExecutionException {
//        String authToken = "0000";// нужно вставить токен
        boolean acquired = permits.tryAcquire(1, TimeUnit.SECONDS);
        if (!acquired) {
            throw new RuntimeException("Unable to acquire permit to execute request");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
//                    .header("Authorization", "Bearer " + authToken)
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status code: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        } finally {
            scheduler.schedule(() -> permits.release(), 1, TimeUnit.SECONDS);
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public List<Product> products;
        public String reg_date;
        public String reg_number;

        public Document(String participantInn, String docId, String docStatus, boolean importRequest, String ownerInn, String producerInn, String productionDate, String productionType, List<Product> products, String regDate, String regNumber) {
            this.description = new Description(participantInn);
            this.doc_id = docId;
            this.doc_status = docStatus;
            this.doc_type = "LP_INTRODUCE_GOODS";
            this.importRequest = importRequest;
            this.owner_inn = ownerInn;
            this.participant_inn = participantInn;
            this.producer_inn = producerInn;
            this.production_date = productionDate;
            this.production_type = productionType;
            this.products = products;
            this.reg_date = regDate;
            this.reg_number = regNumber;
        }
    }

    public static class Description {
        @JsonProperty("participantInn")
        public String participantInn;

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class Product {
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;

        public Product(String certificateDocument, String certificateDocumentDate, String certificateDocumentNumber, String ownerInn, String producerInn, String productionDate, String tnvedCode, String uitCode, String uituCode) {
            this.certificate_document = certificateDocument;
            this.certificate_document_date = certificateDocumentDate;
            this.certificate_document_number = certificateDocumentNumber;
            this.owner_inn = ownerInn;
            this.producer_inn = producerInn;
            this.production_date = productionDate;
            this.tnved_code = tnvedCode;
            this.uit_code = uitCode;
            this.uitu_code = uituCode;
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Product product = new Product("certDoc", "2020-01-23", "certNumber", "ownerInn", "producerInn", "2020-01-23", "tnvedCode", "uitCode", "uituCode");
        List<Product> products = Collections.singletonList(product);

        Document document = new Document("participantInn", "docId", "docStatus", true, "ownerInn", "producerInn", "2020-01-23", "productionType", products, "2020-01-23", "regNumber");

        String signature = "exampleSignature";

        try {
            api.createDocument(document, signature);
        } catch (InterruptedException | IOException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
