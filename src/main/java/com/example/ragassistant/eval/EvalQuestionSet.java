package com.example.ragassistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 프로젝트 루트 eval/questions.json 을 읽는다.
 * 경로는 실행 시 --rag.eval.questions=... 로 덮어쓸 수 있게 Runner에서 처리.
 */
@Component
public class EvalQuestionSet {
    private final ObjectMapper objectMapper;
    public EvalQuestionSet(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    public List<EvalQuestion> load(Path path) throws IOException {
        record Root(String version, String description, List<EvalQuestion> questions) {}
        Root root = objectMapper.readValue(path.toFile(), Root.class);
        return root.questions();
    }
}
