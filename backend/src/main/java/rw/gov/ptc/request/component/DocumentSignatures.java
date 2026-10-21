package rw.gov.ptc.request.component;

import java.util.Map;

/** Checks that an upload's bytes match its declared format, so a renamed file cannot pass as a PDF. */
public final class DocumentSignatures {

    private DocumentSignatures() {
    }

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final Map<String, byte[]> BY_TYPE = Map.of(
            "application/pdf", PDF,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP,
            "application/msword", OLE,
            "image/png", PNG,
            "image/jpeg", JPEG);

    /** True when the content starts with the signature of the type (types without a known signature pass). */
    public static boolean matches(String contentType, byte[] content) {
        byte[] sig = BY_TYPE.get(contentType);
        if (sig == null) {
            return true;
        }
        if (content.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (content[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }
}
