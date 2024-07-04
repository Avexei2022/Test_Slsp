package ru.selsup.kolodin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.*;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Класс для работы с API
 */
public class CrptApi {

    private final SimpleDateFormat dateFormat;
    private final Service service;
    private final RateLimiter rateLimiter;

    /**
     * Конструктор класса
     * @param timeUnit интервал времени
     * @param requestLimit максимальное количество запросов в заданный интервал времени
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        // Конструкция позволяющая регулировать скорость обработки
        rateLimiter = RateLimiter.create((double) requestLimit /timeUnit.toSeconds(1L));
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        service = new Service();
    }

    /**
     * Точка входа
     * @param args
     */
    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 1);
        Service service = crptApi.service;

        // Создать экземпляр продукта
        ArrayList<Product> products = new ArrayList<>();
        try {
            products.add(service.createNewProduct("string"
            , "2020-01-23", "string"
            , "string", "string", "2020-01-23"
            , "string", "string", "string"));
        } catch (ParseException ignored) {
        }

        // Создать экземпляр документа
        Document document = null;
         try {
            document = service.createNewDocument(service.createNewParticipant("string")
                    , "string", "string", DocType.LP_INTRODUCE_GOODS, true
                    , "string", "string","string","2020-01-23"
                    , "string", products,"2020-01-23", "string");
        } catch (ParseException ignored) {
        }

        // Тестовый вызов метода 5 раз в 3-х потоках для проверки ограничений.
        for (int i = 0; i < 3; i++) {
            Document finalDocument = document;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    crptApi.doPostDocument(finalDocument, "signature");
                }
            });
            thread.start();
        }
    }

    /**
     * Реализация единственного метода по
     * "созданию документа для ввода в оборот товара, произведенного в РФ",
     * в соответствии с тестовым заданием
     * @param document - объект документа
     * @param signature - подпись
     */
    public void doPostDocument(Document document, String signature) {
        rateLimiter.acquire();
        URL url = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection httpURLConnection = null;
        try {
            service.trustAllHosts();
            url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
            httpURLConnection = (HttpsURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.setRequestProperty("Charset", "UTF-8");
            httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            httpURLConnection.setRequestProperty("Accept", "application/json");

            String jsonDocument = new ObjectMapper()
                        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
                        .writeValueAsString(document);
            if (jsonDocument != null) {
                try (OutputStream outputStream = httpURLConnection.getOutputStream()) {
                    byte[] writebytes = jsonDocument.getBytes(StandardCharsets.UTF_8);
                    result.append(jsonDocument);
                    outputStream.write(writebytes, 0, writebytes.length);
                    outputStream.flush();
                }
            }
            if (httpURLConnection.getResponseCode() == 200) {
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), StandardCharsets.UTF_8))){
                    result.append("Code() == 200: ").append(bufferedReader.readLine());
                }
            }
        } catch (Exception e) {
            result.append("Exception 1: ").append(e.getMessage());
        }
        System.out.println(result);
    }

    // ВНУТРЕННИЕ КЛАССЫ

    /**
     * Класс документа
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class Document {
        private Participant description;
        private String doc_id;
        private String doc_status;
        private DocType doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private Date production_date;
        private String production_type;
        private ArrayList<Product> products;
        private Date reg_date;
        private String reg_number;
    }

    /**
     * Класс продукта
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
      public class Product {
        private String certificate_document;
        private Date certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    /**
     * Класс участника
     */
    @Data
    @AllArgsConstructor
    public class Participant {
        private String participantInn;
    }

    /**
     * Список Типов документов
     */
    @RequiredArgsConstructor
    @Getter
    private enum DocType {
        LP_INTRODUCE_GOODS;
    }

    /**
     * Класс сервисных методов
     */
    public class Service {
        /**
         * Создать нового участника
         * @param string
         * @return
         */
        private Participant createNewParticipant(String string) {
            return new Participant(string);
        }

        /**
         * Создать новый продукт
         * @param certificate_document
         * @param certificate_document_date
         * @param certificate_document_number
         * @param owner_inn
         * @param producer_inn
         * @param production_date
         * @param tnved_code
         * @param uit_code
         * @param uitu_code
         * @return продукт
         * @throws ParseException исключение при неверном формате входящей даты
         */
        private Product createNewProduct(String certificate_document
                , String certificate_document_date
                , String certificate_document_number
                , String owner_inn
                , String producer_inn
                , String production_date
                , String tnved_code
                , String uit_code
                , String uitu_code) throws ParseException {
            Product product = new Product();
            product.setCertificate_document(certificate_document);
            product.setCertificate_document_date(dateFormat.parse(certificate_document_date));
            product.setCertificate_document_number(certificate_document_number);
            product.setOwner_inn(owner_inn);
            product.setProducer_inn(producer_inn);
            product.setProduction_date(dateFormat.parse(production_date));
            product.setTnved_code(tnved_code);
            product.setUit_code(uit_code);
            product.setUitu_code(uitu_code);
            return product;
        }

        /**
         * Создать новый документ
         * @param description
         * @param doc_id
         * @param doc_status
         * @param doc_type
         * @param importRequest
         * @param owner_inn
         * @param participant_inn
         * @param producer_inn
         * @param production_date
         * @param production_type
         * @param products
         * @param reg_date
         * @param reg_number
         * @return новый документ
         * @throws ParseException исключение при неверном формате входящей даты
         */
        private Document createNewDocument(Participant description
                , String doc_id
                , String doc_status
                , DocType doc_type
                , boolean importRequest
                , String owner_inn
                , String participant_inn
                , String producer_inn
                , String production_date,
                                           String production_type
                , ArrayList<Product> products
                , String reg_date
                , String reg_number) throws ParseException {
            Document document = new Document();
            document.setDescription(description);
            document.setDoc_id(doc_id);
            document.setDoc_status(doc_status);
            document.setDoc_type(doc_type);
            document.setImportRequest(importRequest);
            document.setOwner_inn(owner_inn);
            document.setParticipant_inn(participant_inn);
            document.setProducer_inn(producer_inn);
            document.setProduction_date(dateFormat.parse(production_date));
            document.setProduction_type(production_type);
            document.setProducts(products);
            document.setReg_date(dateFormat.parse(reg_date));
            document.setReg_number(reg_number);
            return document;
        }


        public void trustAllHosts() {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }

                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {

                }

                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {

                }
            }
            };

            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }


}
