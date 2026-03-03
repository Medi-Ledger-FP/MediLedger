package com.mediledger.service;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

/**
 * Shamir's Secret Sharing (SSS) Service
 *
 * Implements a (k, n) threshold scheme:
 * - A secret S is split into n shares
 * - Any k of those shares can reconstruct S
 * - Fewer than k shares reveal nothing about S
 *
 * Used for emergency access: the master decryption key is split among
 * n trusted stakeholders; a quorum of k must approve to reconstruct it.
 *
 * Algorithm:
 * 1. Choose a random polynomial f(x) of degree k-1 over GF(p)
 * where f(0) = secret
 * 2. Shares are (i, f(i)) for i = 1..n
 * 3. Reconstruction uses Lagrange interpolation to find f(0)
 */
@Service
public class ShamirSecretSharingService {

    // Large prime for GF(p) arithmetic — larger than any AES-256 key value
    private static final BigInteger PRIME = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);

    private final SecureRandom random = new SecureRandom();

    // ── Split ────────────────────────────────────────────────────────────────

    /**
     * Split a secret byte array into n shares requiring k to reconstruct.
     *
     * @param secret The secret bytes (e.g. AES-256 key = 32 bytes)
     * @param n      Total number of shares to generate
     * @param k      Minimum shares needed to reconstruct (threshold)
     * @return List of n SecretShare objects
     */
    public List<SecretShare> split(byte[] secret, int n, int k) {
        if (k > n)
            throw new IllegalArgumentException("k must be <= n");
        if (k < 2)
            throw new IllegalArgumentException("k must be >= 2");

        BigInteger secretInt = new BigInteger(1, secret);

        // Build random polynomial: f(x) = secret + a1*x + a2*x^2 + ... + a(k-1)*x^(k-1)
        BigInteger[] coefficients = new BigInteger[k];
        coefficients[0] = secretInt;
        for (int i = 1; i < k; i++) {
            coefficients[i] = new BigInteger(PRIME.bitLength(), random).mod(PRIME);
        }

        // Evaluate f(i) for i = 1..n
        List<SecretShare> shares = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            BigInteger x = BigInteger.valueOf(i);
            BigInteger y = evaluate(coefficients, x);
            shares.add(new SecretShare(i, y));
        }
        return shares;
    }

    /**
     * Reconstruct the secret from any k shares using Lagrange interpolation.
     *
     * @param shares At least k shares
     * @return The original secret bytes
     */
    public byte[] reconstruct(List<SecretShare> shares) {
        BigInteger secret = BigInteger.ZERO;

        for (int i = 0; i < shares.size(); i++) {
            BigInteger xi = BigInteger.valueOf(shares.get(i).x());
            BigInteger yi = shares.get(i).y();

            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < shares.size(); j++) {
                if (i == j)
                    continue;
                BigInteger xj = BigInteger.valueOf(shares.get(j).x());
                numerator = numerator.multiply(xj.negate()).mod(PRIME);
                denominator = denominator.multiply(xi.subtract(xj)).mod(PRIME);
            }

            BigInteger lagrange = yi
                    .multiply(numerator)
                    .multiply(denominator.modInverse(PRIME))
                    .mod(PRIME);

            secret = secret.add(lagrange).mod(PRIME);
        }

        // Convert back to fixed-length byte array (pad to 32 bytes for AES-256)
        byte[] raw = secret.toByteArray();
        if (raw.length == 32)
            return raw;
        if (raw.length > 32) {
            // Strip leading zero byte added by BigInteger for sign
            return Arrays.copyOfRange(raw, raw.length - 32, raw.length);
        }
        // Pad with leading zeros
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
        return padded;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /** Encode a share as "x:yHex" for storage/transmission */
    public String encodeShare(SecretShare share) {
        return share.x() + ":" + share.y().toString(16);
    }

    /** Decode a share from "x:yHex" */
    public SecretShare decodeShare(String encoded) {
        String[] parts = encoded.split(":", 2);
        return new SecretShare(Integer.parseInt(parts[0]), new BigInteger(parts[1], 16));
    }

    // ── Polynomial evaluation ─────────────────────────────────────────────────

    private BigInteger evaluate(BigInteger[] coefficients, BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        BigInteger xPow = BigInteger.ONE;
        for (BigInteger coeff : coefficients) {
            result = result.add(coeff.multiply(xPow)).mod(PRIME);
            xPow = xPow.multiply(x).mod(PRIME);
        }
        return result;
    }

    // ── Data record ──────────────────────────────────────────────────────────

    /** A single Shamir share: (x-coordinate, y-coordinate) */
    public record SecretShare(int x, BigInteger y) {
    }
}
