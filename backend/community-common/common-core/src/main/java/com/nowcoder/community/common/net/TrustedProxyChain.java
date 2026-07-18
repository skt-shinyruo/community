package com.nowcoder.community.common.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a client IP by stripping explicitly trusted proxies from the right side of a forwarded chain.
 */
public final class TrustedProxyChain {

    private static final int MAX_FORWARDED_HOPS = 32;
    private static final int MAX_HOP_LENGTH = 64;
    private static final int IPV4_BYTES = 4;
    private static final int IPV6_BYTES = 16;

    private final List<Cidr> trustedNetworks;

    public TrustedProxyChain(List<String> trustedCidrs) {
        if (trustedCidrs == null) {
            throw new IllegalArgumentException("Trusted CIDRs must not be null");
        }

        List<Cidr> parsedNetworks = new ArrayList<>(trustedCidrs.size());
        for (String trustedCidr : trustedCidrs) {
            parsedNetworks.add(parseCidr(trustedCidr));
        }
        this.trustedNetworks = List.copyOf(parsedNetworks);
    }

    public Resolution resolve(String directPeer, List<String> forwardedFor) {
        Address directAddress = parseAddress(directPeer);
        if (directAddress == null) {
            return new Resolution(null, Source.DIRECT_PEER);
        }

        Resolution directResolution = new Resolution(directAddress.normalizedText(), Source.DIRECT_PEER);
        if (!isTrusted(directAddress)) {
            return directResolution;
        }
        if (forwardedFor == null || forwardedFor.isEmpty() || forwardedFor.size() > MAX_FORWARDED_HOPS) {
            return directResolution;
        }

        List<Address> forwardedAddresses = new ArrayList<>(forwardedFor.size());
        for (String hop : forwardedFor) {
            Address forwardedAddress = parseHeaderHop(hop);
            if (forwardedAddress == null) {
                return directResolution;
            }
            forwardedAddresses.add(forwardedAddress);
        }

        for (int index = forwardedAddresses.size() - 1; index >= 0; index--) {
            Address forwardedAddress = forwardedAddresses.get(index);
            if (!isTrusted(forwardedAddress)) {
                return new Resolution(forwardedAddress.normalizedText(), Source.FORWARDED_CHAIN);
            }
        }
        return directResolution;
    }

    private boolean isTrusted(Address address) {
        for (Cidr trustedNetwork : trustedNetworks) {
            if (trustedNetwork.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static Address parseHeaderHop(String hop) {
        if (hop == null || hop.isEmpty() || hop.length() > MAX_HOP_LENGTH || containsControlCharacter(hop)) {
            return null;
        }

        int start = 0;
        int end = hop.length();
        while (start < end && hop.charAt(start) == ' ') {
            start++;
        }
        while (end > start && hop.charAt(end - 1) == ' ') {
            end--;
        }
        if (start == end) {
            return null;
        }
        return parseAddress(hop.substring(start, end));
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static Cidr parseCidr(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Trusted CIDR must not be null");
        }

        int separator = value.indexOf('/');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf('/', separator + 1) >= 0) {
            throw invalidCidr(value);
        }

        Address address = parseAddress(value.substring(0, separator));
        if (address == null) {
            throw invalidCidr(value);
        }

        int prefixLength = parsePrefixLength(value, separator + 1);
        int addressBits = address.bytes().length * Byte.SIZE;
        if (prefixLength < 0 || prefixLength > addressBits) {
            throw invalidCidr(value);
        }
        return new Cidr(mask(address.bytes(), prefixLength), prefixLength);
    }

    private static int parsePrefixLength(String value, int start) {
        int prefixLength = 0;
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
            prefixLength = prefixLength * 10 + character - '0';
            if (prefixLength > 128) {
                return -1;
            }
        }
        return prefixLength;
    }

    private static IllegalArgumentException invalidCidr(String value) {
        return new IllegalArgumentException("Invalid trusted CIDR: " + value);
    }

    private static byte[] mask(byte[] address, int prefixLength) {
        byte[] network = address.clone();
        int completeBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        int firstClearedByte = completeBytes;
        if (remainingBits != 0) {
            int bitMask = 0xff << (Byte.SIZE - remainingBits);
            network[completeBytes] = (byte) (network[completeBytes] & bitMask);
            firstClearedByte++;
        }
        for (int index = firstClearedByte; index < network.length; index++) {
            network[index] = 0;
        }
        return network;
    }

    private static Address parseAddress(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_HOP_LENGTH) {
            return null;
        }
        if (value.indexOf(':') >= 0) {
            return parseIpv6(value);
        }
        return parseIpv4(value);
    }

    private static Address parseIpv4(String value) {
        if (value.length() < 7 || value.length() > 15) {
            return null;
        }

        byte[] bytes = new byte[IPV4_BYTES];
        int offset = 0;
        for (int octetIndex = 0; octetIndex < IPV4_BYTES; octetIndex++) {
            if (offset >= value.length()) {
                return null;
            }

            int octet = 0;
            int digits = 0;
            while (offset < value.length() && value.charAt(offset) != '.') {
                char character = value.charAt(offset);
                if (character < '0' || character > '9' || digits == 3) {
                    return null;
                }
                octet = octet * 10 + character - '0';
                digits++;
                offset++;
            }
            if (digits == 0 || octet > 255) {
                return null;
            }
            bytes[octetIndex] = (byte) octet;

            if (octetIndex < IPV4_BYTES - 1) {
                if (offset >= value.length() || value.charAt(offset) != '.') {
                    return null;
                }
                offset++;
            } else if (offset != value.length()) {
                return null;
            }
        }
        return new Address(bytes, formatIpv4(bytes), false);
    }

    private static Address parseIpv6(String value) {
        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 2) >= 0) {
            return null;
        }

        List<Integer> words;
        if (compression < 0) {
            words = parseIpv6Section(value, true);
            if (words == null || words.size() != 8) {
                return null;
            }
        } else {
            List<Integer> leftWords = parseIpv6Section(value.substring(0, compression), false);
            List<Integer> rightWords = parseIpv6Section(value.substring(compression + 2), true);
            if (leftWords == null || rightWords == null || leftWords.size() + rightWords.size() >= 8) {
                return null;
            }

            words = new ArrayList<>(8);
            words.addAll(leftWords);
            int compressedWords = 8 - leftWords.size() - rightWords.size();
            for (int index = 0; index < compressedWords; index++) {
                words.add(0);
            }
            words.addAll(rightWords);
        }

        byte[] bytes = new byte[IPV6_BYTES];
        for (int index = 0; index < words.size(); index++) {
            int word = words.get(index);
            bytes[index * 2] = (byte) (word >>> Byte.SIZE);
            bytes[index * 2 + 1] = (byte) word;
        }

        boolean ipv4Mapped = isIpv4Mapped(bytes);
        String normalizedText = ipv4Mapped
                ? formatIpv4(bytes, IPV6_BYTES - IPV4_BYTES)
                : formatIpv6(bytes);
        return new Address(bytes, normalizedText, ipv4Mapped);
    }

    private static List<Integer> parseIpv6Section(String section, boolean allowIpv4AtEnd) {
        List<Integer> words = new ArrayList<>();
        if (section.isEmpty()) {
            return words;
        }
        if (section.charAt(0) == ':' || section.charAt(section.length() - 1) == ':') {
            return null;
        }

        int tokenStart = 0;
        while (tokenStart < section.length()) {
            int separator = section.indexOf(':', tokenStart);
            int tokenEnd = separator < 0 ? section.length() : separator;
            if (tokenEnd == tokenStart) {
                return null;
            }

            String token = section.substring(tokenStart, tokenEnd);
            boolean lastToken = tokenEnd == section.length();
            if (token.indexOf('.') >= 0) {
                if (!allowIpv4AtEnd || !lastToken) {
                    return null;
                }
                Address ipv4 = parseIpv4(token);
                if (ipv4 == null) {
                    return null;
                }
                byte[] ipv4Bytes = ipv4.bytes();
                words.add(unsigned(ipv4Bytes[0]) << Byte.SIZE | unsigned(ipv4Bytes[1]));
                words.add(unsigned(ipv4Bytes[2]) << Byte.SIZE | unsigned(ipv4Bytes[3]));
            } else {
                int word = parseHexWord(token);
                if (word < 0) {
                    return null;
                }
                words.add(word);
            }

            if (lastToken) {
                break;
            }
            tokenStart = tokenEnd + 1;
        }
        return words;
    }

    private static int parseHexWord(String token) {
        if (token.isEmpty() || token.length() > 4) {
            return -1;
        }

        int word = 0;
        for (int index = 0; index < token.length(); index++) {
            int digit = hexDigit(token.charAt(index));
            if (digit < 0) {
                return -1;
            }
            word = word << 4 | digit;
        }
        return word;
    }

    private static int hexDigit(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return unsigned(bytes[10]) == 0xff && unsigned(bytes[11]) == 0xff;
    }

    private static String formatIpv4(byte[] bytes) {
        return formatIpv4(bytes, 0);
    }

    private static String formatIpv4(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) + "."
                + unsigned(bytes[offset + 1]) + "."
                + unsigned(bytes[offset + 2]) + "."
                + unsigned(bytes[offset + 3]);
    }

    private static String formatIpv6(byte[] bytes) {
        int[] words = new int[8];
        for (int index = 0; index < words.length; index++) {
            words[index] = unsigned(bytes[index * 2]) << Byte.SIZE | unsigned(bytes[index * 2 + 1]);
        }

        int longestZeroStart = -1;
        int longestZeroLength = 0;
        for (int index = 0; index < words.length; ) {
            if (words[index] != 0) {
                index++;
                continue;
            }
            int start = index;
            while (index < words.length && words[index] == 0) {
                index++;
            }
            int length = index - start;
            if (length >= 2 && length > longestZeroLength) {
                longestZeroStart = start;
                longestZeroLength = length;
            }
        }

        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            if (index == longestZeroStart) {
                normalized.append("::");
                index += longestZeroLength - 1;
                continue;
            }
            if (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) != ':') {
                normalized.append(':');
            }
            normalized.append(Integer.toHexString(words[index]));
        }
        return normalized.toString();
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    public record Resolution(String clientIp, Source source) {
    }

    public enum Source {
        DIRECT_PEER,
        FORWARDED_CHAIN
    }

    private record Address(byte[] bytes, String normalizedText, boolean ipv4Mapped) {
    }

    private static final class Cidr {

        private final byte[] network;
        private final int prefixLength;
        private final boolean ipv4MappedRange;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
            this.ipv4MappedRange = network.length == IPV6_BYTES
                    && prefixLength >= 96
                    && isIpv4MappedPrefix(network);
        }

        private boolean contains(Address address) {
            if (address.ipv4Mapped()) {
                if (network.length == IPV4_BYTES) {
                    return prefixMatches(address.bytes(), IPV6_BYTES - IPV4_BYTES, network, 0, prefixLength);
                }
                if (ipv4MappedRange) {
                    return prefixMatches(
                            address.bytes(),
                            IPV6_BYTES - IPV4_BYTES,
                            network,
                            IPV6_BYTES - IPV4_BYTES,
                            prefixLength - 96
                    );
                }
                return false;
            }

            if (address.bytes().length == network.length) {
                return prefixMatches(address.bytes(), 0, network, 0, prefixLength);
            }
            if (address.bytes().length == IPV4_BYTES && ipv4MappedRange) {
                return prefixMatches(
                        address.bytes(),
                        0,
                        network,
                        IPV6_BYTES - IPV4_BYTES,
                        prefixLength - 96
                );
            }
            return false;
        }

        private static boolean isIpv4MappedPrefix(byte[] network) {
            for (int index = 0; index < 10; index++) {
                if (network[index] != 0) {
                    return false;
                }
            }
            return unsigned(network[10]) == 0xff && unsigned(network[11]) == 0xff;
        }

        private static boolean prefixMatches(
                byte[] address,
                int addressOffset,
                byte[] network,
                int networkOffset,
                int bits
        ) {
            int completeBytes = bits / Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (address[addressOffset + index] != network[networkOffset + index]) {
                    return false;
                }
            }

            int remainingBits = bits % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int bitMask = 0xff << (Byte.SIZE - remainingBits);
            return (address[addressOffset + completeBytes] & bitMask)
                    == (network[networkOffset + completeBytes] & bitMask);
        }
    }
}
