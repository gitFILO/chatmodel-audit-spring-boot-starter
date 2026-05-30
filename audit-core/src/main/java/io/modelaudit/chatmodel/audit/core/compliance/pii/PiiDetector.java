package io.modelaudit.chatmodel.audit.core.compliance.pii;

// External starter registers its own detector @Bean via this interface; PiiMaskService auto-discovers
public interface PiiDetector {

    String id();

    String mask(String input);
}
