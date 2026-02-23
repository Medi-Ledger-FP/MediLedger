package com.mediledger.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * IPFS Service using Pinata Cloud
 * Handles decentralized file storage
 */
@Service
public class IPFSService {

    @Value("${ipfs.pinata.api.key:}")
    private String pinataApiKey;

    @Value("${ipfs.pinata.secret.key:}")
    private String pinataSecretKey;

    private static final String PINATA_UPLOAD_URL = "https://api.pinata.cloud/pinning/pinFileToIPFS";
    private static final String PINATA_GATEWAY_URL = "https://gateway.pinata.cloud/ipfs/";

    // Simulation cache: CID -> encrypted bytes (used when Pinata not configured)
    private final java.util.Map<String, byte[]> simulationCache = new java.util.concurrent.ConcurrentHashMap<>();

    public String uploadFile(byte[] file, String fileName) throws IOException {
        // Fast-exit simulation: no network call when Pinata not configured
        if (pinataApiKey == null || pinataApiKey.isEmpty()) {
            return simulateUpload(file, fileName);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(PINATA_UPLOAD_URL).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("pinata_api_key", pinataApiKey);
        connection.setRequestProperty("pinata_secret_api_key", pinataSecretKey);

        String boundary = "----" + System.currentTimeMillis();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName)
                    .append("\"\r\n");
            writer.append("Content-Type: application/octet-stream\r\n\r\n");
            writer.flush();
            os.write(file);
            os.flush();
            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                return extractCIDFromResponse(response.toString());
            }
        } else {
            throw new IOException("Failed to upload to IPFS. Response code: " + responseCode);
        }
    }

    public byte[] downloadFile(String cid) throws IOException {
        if (cid.startsWith("QmSIM_")) {
            // Return the actual encrypted bytes stored during upload
            byte[] cached = simulationCache.get(cid);
            if (cached != null) {
                System.out.println("📦 Simulation cache hit for " + cid + " (" + cached.length + " bytes)");
                return cached;
            }
            // Fallback if cache was cleared (e.g. server restart)
            throw new IOException("Simulated IPFS: no cached data for " + cid + ". Please re-upload the file.");
        }

        URL url = new URL(PINATA_GATEWAY_URL + cid);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream is = connection.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            }
        } else {
            throw new IOException("Failed to download from IPFS. Response code: " + responseCode);
        }
    }

    public boolean verifyCID(String cid) {
        try {
            URL url = new URL(PINATA_GATEWAY_URL + cid);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    private String simulateUpload(byte[] fileBytes, String fileName) {
        // Generate a deterministic fake CID from file content hash
        String input = fileName + fileBytes.length;
        String sanitised = Base64.getEncoder().encodeToString(input.getBytes()).replaceAll("[^a-zA-Z0-9]", "");
        String cid = "QmSIM_" + sanitised.substring(0, Math.min(20, sanitised.length()));
        // Cache the encrypted bytes so download can retrieve and decrypt them
        simulationCache.put(cid, fileBytes);
        System.out.println("⚠️  Simulated IPFS upload: " + cid + " (" + fileName + ", " + fileBytes.length
                + " bytes) — cached for download");
        return cid;
    }

    private String extractCIDFromResponse(String jsonResponse) {
        int start = jsonResponse.indexOf("\"IpfsHash\":\"") + 12;
        int end = jsonResponse.indexOf("\"", start);
        if (start > 11 && end > start) {
            return jsonResponse.substring(start, end);
        }
        throw new RuntimeException("Failed to parse CID from Pinata response");
    }
}
