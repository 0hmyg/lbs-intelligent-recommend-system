package org.example.bootstrap;

import org.example.domain.Post;
import org.example.repo.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class PostImageBackfillRunner implements CommandLineRunner {
    private final PostRepository postRepository;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public PostImageBackfillRunner(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        List<Post> posts = postRepository.findAll();
        boolean changed = false;
        for (Post post : posts) {
            if (post.getDeletedAt() != null) continue;
            if (post.getImages() != null && post.getImages().length > 0) continue;
            String imagePath = createPlaceholder(post);
            post.setImages(new String[]{imagePath});
            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);
            changed = true;
        }
        if (changed) {
            System.out.println("[PostImageBackfillRunner] historical posts images backfilled");
        }
    }

    private String createPlaceholder(Post post) throws IOException {
        Path dir = Paths.get(uploadDir, "posts");
        Files.createDirectories(dir);
        String name = "scenery-" + post.getId() + "-" + UUID.randomUUID() + ".svg";
        Path target = dir.resolve(name);
        long seed = post.getId() == null ? System.nanoTime() : post.getId();
        String label = escapeXml(post.getTitle() == null ? "未命名帖子" : post.getTitle());
        String category = escapeXml(post.getCategory() == null ? "post" : post.getCategory());
        String sky = palette(seed, 0);
        String mountain = palette(seed, 1);
        String grass = palette(seed, 2);
        String lake = palette(seed, 3);
        String sun = palette(seed, 4);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='1200' height='800' viewBox='0 0 1200 800'>"
                + "<defs>"
                + "<linearGradient id='sky' x1='0' y1='0' x2='0' y2='1'><stop offset='0%' stop-color='" + sky + "'/><stop offset='100%' stop-color='#dbeafe'/></linearGradient>"
                + "<linearGradient id='lake' x1='0' y1='0' x2='0' y2='1'><stop offset='0%' stop-color='" + lake + "'/><stop offset='100%' stop-color='#0f766e'/></linearGradient>"
                + "</defs>"
                + "<rect width='1200' height='800' fill='url(#sky)'/>"
                + "<circle cx='980' cy='150' r='70' fill='" + sun + "' opacity='0.95'/>"
                + "<path d='M0 560 C 130 500, 220 460, 360 520 C 470 565, 560 420, 700 470 C 830 520, 930 410, 1200 500 L 1200 800 L 0 800 Z' fill='" + grass + "'/>"
                + "<path d='M120 520 L 300 250 L 420 520 Z' fill='" + mountain + "' opacity='0.95'/>"
                + "<path d='M320 520 L 500 180 L 720 520 Z' fill='" + mountain + "' opacity='0.78'/>"
                + "<path d='M640 520 L 860 230 L 1080 520 Z' fill='" + mountain + "' opacity='0.86'/>"
                + "<ellipse cx='620' cy='680' rx='420' ry='95' fill='url(#lake)' opacity='0.85'/>"
                + "<rect x='70' y='80' width='1060' height='640' rx='34' fill='rgba(255,255,255,0.14)' stroke='rgba(255,255,255,0.26)'/>"
                + "<text x='600' y='210' text-anchor='middle' font-size='56' font-family='Microsoft YaHei, Arial, sans-serif' fill='white'>风景补图</text>"
                + "<text x='600' y='290' text-anchor='middle' font-size='34' font-family='Microsoft YaHei, Arial, sans-serif' fill='white'>" + label + "</text>"
                + "<text x='600' y='344' text-anchor='middle' font-size='24' font-family='Microsoft YaHei, Arial, sans-serif' fill='rgba(255,255,255,0.9)'>分类：" + category + "</text>"
                + "</svg>";
        Files.write(target, svg.getBytes(StandardCharsets.UTF_8));
        return "/uploads/posts/" + name;
    }

    private String palette(long seed, int offset) {
        int value = Math.abs((int) ((seed + offset * 7919) % 360));
        return "hsl(" + value + ", 65%, " + (offset == 0 ? "72%" : offset == 1 ? "52%" : offset == 2 ? "44%" : offset == 3 ? "58%" : "66%") + ")";
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
