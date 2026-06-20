package org.example.admin;

import org.example.common.ApiResponse;
import org.example.domain.SensitiveWord;
import org.example.repo.SensitiveWordRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/sensitive-words")
public class AdminSensitiveWordController {
    private final SensitiveWordRepository sensitiveWordRepository;

    public AdminSensitiveWordController(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    @GetMapping
    public ApiResponse<List<SensitiveWord>> list() {
        return ApiResponse.ok(sensitiveWordRepository.findAll());
    }

    public static class WordBody {
        private String word;
        private String level;
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }

    @PostMapping
    @Transactional
    public ApiResponse<SensitiveWord> create(@RequestBody WordBody body) {
        if (body == null || body.getWord() == null || body.getWord().trim().isEmpty()) {
            throw new IllegalArgumentException("敏感词不能为空");
        }
        String word = body.getWord().trim();
        sensitiveWordRepository.findByWordIgnoreCase(word).ifPresent(w -> { throw new IllegalArgumentException("敏感词已存在"); });
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word);
        sw.setLevel(body.getLevel() == null || body.getLevel().trim().isEmpty() ? "block" : body.getLevel().trim());
        sw.setCreatedAt(LocalDateTime.now());
        sw.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(sensitiveWordRepository.save(sw));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<SensitiveWord> update(@PathVariable Long id, @RequestBody WordBody body) {
        SensitiveWord sw = sensitiveWordRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        if (body != null && body.getWord() != null && !body.getWord().trim().isEmpty()) {
            String word = body.getWord().trim();
            sensitiveWordRepository.findByWordIgnoreCase(word).ifPresent(exist -> {
                if (!exist.getId().equals(id)) {
                    throw new IllegalArgumentException("敏感词已存在");
                }
            });
            sw.setWord(word);
        }
        if (body != null && body.getLevel() != null && !body.getLevel().trim().isEmpty()) {
            sw.setLevel(body.getLevel().trim());
        }
        sw.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(sensitiveWordRepository.save(sw));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Object> delete(@PathVariable Long id) {
        sensitiveWordRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }
}
