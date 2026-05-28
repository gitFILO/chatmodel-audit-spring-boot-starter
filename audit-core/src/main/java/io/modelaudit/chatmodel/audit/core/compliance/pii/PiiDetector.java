package io.modelaudit.chatmodel.audit.core.compliance.pii;

// 외부 starter가 동일 인터페이스로 자기 detector @Bean 등록하면 PiiMaskService가 자동 발견
public interface PiiDetector {

    String id();

    String mask(String input);
}
