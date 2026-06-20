package org.example.post;

import org.example.auth.UserPrincipal;
import org.example.common.ApiResponse;
import org.example.config.PythonModerationClient;
import org.example.domain.LikeRecord;
import org.example.domain.Post;
import org.example.domain.User;
import org.example.domain.UserAction;
import org.example.geo.GeoUtils;
import org.example.post.dto.CreatePostRequest;
import org.example.post.dto.PostResponse;
import org.example.repo.LikeRepository;
import org.example.repo.PostRepository;
import org.example.repo.UserActionRepository;
import org.example.repo.UserRepository;
import org.example.service.PostTaggingService;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final UserActionRepository userActionRepository;
    private final PythonModerationClient moderationClient;
    private final PostTaggingService postTaggingService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public PostController(PostRepository postRepository,
                          UserRepository userRepository,
                          LikeRepository likeRepository,
                          UserActionRepository userActionRepository,
                          PythonModerationClient moderationClient,
                          PostTaggingService postTaggingService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.userActionRepository = userActionRepository;
        this.moderationClient = moderationClient;
        this.postTaggingService = postTaggingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PostResponse> create(Authentication authentication,
                                            @RequestPart("data") @Valid CreatePostRequest req,
                                            @RequestPart(value = "images", required = false) MultipartFile[] images) throws IOException {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Point p = null;
        String locName = req.getLocationName();
        if (req.getLng() != null && req.getLat() != null) {
            p = GeoUtils.point(req.getLng(), req.getLat());
        } else if (user.getLocationGeom() != null) {
            p = user.getLocationGeom();
            if (locName == null) {
                locName = user.getLocationName();
            }
        }

        String auditMode = req.getAuditMode() == null ? "auto" : req.getAuditMode().trim().toLowerCase();
        boolean manualReview = "manual".equals(auditMode);
        String publishText = (req.getTitle() == null ? "" : req.getTitle()) + "\n" + (req.getContent() == null ? "" : req.getContent());
        PythonModerationClient.ModerationResult moderation = manualReview
                ? PythonModerationClient.ModerationResult.pass(null, null)
                : moderationClient.moderate(publishText);

        if (!manualReview && !moderation.isPassed()) {
            Post blocked = new Post();
            blocked.setUserId(principal.getId());
            blocked.setTitle(req.getTitle());
            blocked.setContent(req.getContent());
            blocked.setCategory(req.getCategory());
            blocked.setLocationName(locName);
            blocked.setLocationGeom(p);
            blocked.setImages(null);
            blocked.setViewCount(0);
            blocked.setLikeCount(0);
            blocked.setCommentCount(0);
            blocked.setIsAudited((short) 2);
            blocked.setAuditReason("命中敏感词，已自动拦截：" + formatHitWords(moderation.getHitWords()));
            blocked.setModerationFilteredText(moderation.getFilteredText());
            blocked.setModerationHitWords(formatHitWords(moderation.getHitWords()));
            blocked.setModerationCheckedAt(LocalDateTime.now());
            blocked.setCreatedAt(LocalDateTime.now());
            blocked.setUpdatedAt(LocalDateTime.now());
            postRepository.save(blocked);
            return ApiResponse.fail(blocked.getAuditReason());
        }

        String[] imageUrls = null;
        if (images != null && images.length > 0) {
            imageUrls = saveImages(images);
        }

        Post post = new Post();
        post.setUserId(principal.getId());
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setCategory(req.getCategory());
        post.setLocationName(locName);
        post.setLocationGeom(p);
        post.setImages(imageUrls);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsAudited(manualReview ? (short) 0 : (short) 1);
        post.setAuditReason(manualReview ? "人工待审" : null);
        post.setModerationCheckedAt(LocalDateTime.now());
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        if (!manualReview) {
            post.setModerationFilteredText(moderation.getFilteredText());
            post.setModerationHitWords(formatHitWords(moderation.getHitWords()));
        }
        Post saved = postRepository.save(post);

        if (saved.getIsAudited() != null && saved.getIsAudited() == 1) {
            postTaggingService.generateAndSaveTags(saved);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("post", toResp(saved));
        data.put("moderation_passed", moderation.isPassed());
        data.put("moderation_hit_words", moderation.getHitWords());
        return ApiResponse.ok(toResp(saved));
    }


    private Point resolveCurrentLocation(User user) {
        if (user.getLocationGeom() != null) {
            return user.getLocationGeom();
        }
        return null;
    }

    private String formatHitWords(Object hitWords) {
        if (hitWords == null) return "";
        if (hitWords instanceof java.util.Collection) {
            java.util.Collection<?> collection = (java.util.Collection<?>) hitWords;
            java.util.List<String> list = new java.util.ArrayList<String>();
            for (Object o : collection) {
                if (o != null) list.add(String.valueOf(o));
            }
            return String.join(",", list);
        }
        return String.valueOf(hitWords);
    }

    private String[] saveImages(MultipartFile[] images) throws IOException {
        Path dir = Paths.get(uploadDir, "posts");
        Files.createDirectories(dir);
        String[] urls = new String[images.length];
        for (int i = 0; i < images.length; i++) {
            MultipartFile image = images[i];
            String original = image.getOriginalFilename() == null ? "img" : image.getOriginalFilename();
            String ext = "";
            int idx = original.lastIndexOf('.');
            if (idx >= 0) {
                ext = original.substring(idx);
            }
            String name = UUID.randomUUID() + ext;
            Path target = dir.resolve(name);
            Files.copy(image.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            urls[i] = "/uploads/posts/" + name;
        }
        return urls;
    }

    @GetMapping("/{id}")
    public ApiResponse<PostResponse> detail(@PathVariable Long id) {
        Post p = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (p.getDeletedAt() != null) {
            throw new IllegalArgumentException("帖子已删除");
        }
        if (p.getIsAudited() != null && p.getIsAudited() == 0) {
            p.setAuditReason("人工待审");
        } else if (p.getIsAudited() != null && p.getIsAudited() == 1) {
            p.setAuditReason("通过");
        } else if (p.getIsAudited() != null && p.getIsAudited() == 2) {
            p.setAuditReason("自动拦截");
        } else if (p.getIsAudited() != null && p.getIsAudited() == 3) {
            p.setAuditReason("申述中");
        } else if (p.getIsAudited() != null && p.getIsAudited() == 4) {
            p.setAuditReason("人工驳回");
        }
        return ApiResponse.ok(toResp(p));
    }

    @GetMapping
    public ApiResponse<Page<PostResponse>> list(Authentication authentication,
                                                @RequestParam(defaultValue = "nearby") String mode,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Point currentLocation = resolveCurrentLocation(user);
        if (currentLocation == null) {
            throw new IllegalArgumentException("请先绑定位置");
        }

        double lng = currentLocation.getX();
        double lat = currentLocation.getY();
        List<Object[]> rows = "hot".equalsIgnoreCase(mode)
                ? postRepository.findNearbyHotRows(lng, lat)
                : postRepository.findNearbyRows(lng, lat);

        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim().toLowerCase();
            List<Long> filteredIds = new ArrayList<Long>();
            Map<Long, Double> filteredDistances = new HashMap<Long, Double>();
            for (Object[] row : rows) {
                Long postId = ((Number) row[0]).longValue();
                Post p = postRepository.findById(postId).orElse(null);
                if (p == null || p.getDeletedAt() != null || p.getIsAudited() == null || p.getIsAudited() != 1) continue;
                String hay = ((p.getTitle() == null ? "" : p.getTitle()) + " " +
                        (p.getContent() == null ? "" : p.getContent()) + " " +
                        (p.getCategory() == null ? "" : p.getCategory()) + " " +
                        (p.getLocationName() == null ? "" : p.getLocationName())).toLowerCase();
                if (hay.contains(k)) {
                    filteredIds.add(postId);
                    if (row.length > 1 && row[1] != null) {
                        filteredDistances.put(postId, ((Number) row[1]).doubleValue());
                    }
                }
            }
            rows = new ArrayList<Object[]>();
            for (Long id : filteredIds) {
                rows.add(new Object[]{id, filteredDistances.get(id)});
            }
        }

        Map<Long, Double> distanceMap = new HashMap<Long, Double>();
        List<Long> ids = new ArrayList<Long>();
        for (Object[] row : rows) {
            Long postId = ((Number) row[0]).longValue();
            ids.add(postId);
            if (row.length > 1 && row[1] != null) {
                distanceMap.put(postId, ((Number) row[1]).doubleValue());
            }
        }

        List<PostResponse> items = new ArrayList<PostResponse>();
        List<Post> posts = postRepository.findAllById(ids);
        Map<Long, Post> postMap = new HashMap<Long, Post>();
        for (Post p : posts) {
            postMap.put(p.getId(), p);
        }
        for (Long id : ids) {
            Post p = postMap.get(id);
            if (p == null) continue;
            PostResponse resp = toResp(p);
            resp.setDistanceMeters(distanceMap.get(id));
            items.add(resp);
        }

        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        List<PostResponse> pageItems = items.subList(from, to);
        return ApiResponse.ok(new org.springframework.data.domain.PageImpl<PostResponse>(pageItems, pageable, items.size()));
    }

    @GetMapping("/my")
    public ApiResponse<Page<PostResponse>> myPosts(Authentication authentication,
                                                   @RequestParam(defaultValue = "all") String status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> pageData;

        if ("deleted".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyDeletedPosts(principal.getId(), pageable);
        } else if ("pending".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyPostsByStatus(principal.getId(), (short) 0, pageable);
        } else if ("approved".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyPostsByStatus(principal.getId(), (short) 1, pageable);
        } else if ("auto_blocked".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyPostsByStatus(principal.getId(), (short) 2, pageable);
        } else if ("appealed".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyPostsByStatus(principal.getId(), (short) 3, pageable);
        } else if ("manual_rejected".equalsIgnoreCase(status)) {
            pageData = postRepository.findMyPostsByStatus(principal.getId(), (short) 4, pageable);
        } else {
            pageData = postRepository.findMyActivePosts(principal.getId(), pageable);
        }
        return ApiResponse.ok(pageData.map(PostController::toResp));
    }

    @GetMapping("/admin/rejected")
    public ApiResponse<Page<PostResponse>> adminRejected(Authentication authentication,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (principal.getRole() == null || !"admin".equalsIgnoreCase(principal.getRole())) {
            throw new IllegalArgumentException("无权限");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> rejected = postRepository.findByIsAuditedAndDeletedAtIsNullOrderByCreatedAtDesc((short) 2, pageable);
        return ApiResponse.ok(rejected.map(PostController::toResp));
    }

    @PostMapping("/{id}/appeal")
    @Transactional
    public ApiResponse<Object> appeal(Authentication authentication,
                                      @PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!principal.getId().equals(post.getUserId())) {
            throw new IllegalArgumentException("只能申述自己发布的帖子");
        }
        if (post.getDeletedAt() != null) {
            throw new IllegalArgumentException("帖子已删除");
        }
        if (post.getIsAudited() == null || post.getIsAudited() != 2) {
            throw new IllegalArgumentException("只有拦截状态的帖子才能申述");
        }
        post.setIsAudited((short) 3);
        post.setAuditReason("用户申述");
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        return ApiResponse.ok("申述已提交", null);
    }

    @PostMapping("/{id}/view")
    @Transactional
    public ApiResponse<Object> view(Authentication authentication, @PathVariable Long id) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getDeletedAt() != null) {
            throw new IllegalArgumentException("帖子已删除");
        }
        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        postRepository.save(post);

        UserAction action = new UserAction();
        action.setUserId(principal.getId());
        action.setPostId(id);
        action.setActionType("view");
        action.setActionTime(LocalDateTime.now());
        userActionRepository.save(action);

        return ApiResponse.ok("OK", null);
    }

    @PostMapping("/{id}/like")
    @Transactional
    public ApiResponse<Object> like(Authentication authentication, @PathVariable Long id) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getDeletedAt() != null) {
            throw new IllegalArgumentException("帖子已删除");
        }
        if (principal.getId().equals(post.getUserId())) {
            throw new IllegalArgumentException("不能点赞自己发布的帖子");
        }
        likeRepository.findByUserIdAndTargetTypeAndTargetId(principal.getId(), "post", id)
                .ifPresent(r -> { throw new IllegalArgumentException("已点赞"); });

        LikeRecord rec = new LikeRecord();
        rec.setUserId(principal.getId());
        rec.setTargetType("post");
        rec.setTargetId(id);
        rec.setCreatedAt(LocalDateTime.now());
        likeRepository.save(rec);

        post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
        postRepository.save(post);

        UserAction action = new UserAction();
        action.setUserId(principal.getId());
        action.setPostId(id);
        action.setActionType("like");
        action.setActionTime(LocalDateTime.now());
        userActionRepository.save(action);

        return ApiResponse.ok("OK", null);
    }

    @DeleteMapping("/{id}/like")
    @Transactional
    public ApiResponse<Object> unlike(Authentication authentication, @PathVariable Long id) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        likeRepository.findByUserIdAndTargetTypeAndTargetId(principal.getId(), "post", id)
                .ifPresent(likeRepository::delete);

        int likes = post.getLikeCount() == null ? 0 : post.getLikeCount();
        post.setLikeCount(Math.max(0, likes - 1));
        postRepository.save(post);
        return ApiResponse.ok("OK", null);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Object> deleteMine(Authentication authentication, @PathVariable Long id) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!principal.getId().equals(post.getUserId())) {
            throw new IllegalArgumentException("只能删除自己发布的帖子");
        }
        if (post.getDeletedAt() != null) {
            return ApiResponse.ok("帖子已删除", null);
        }
        post.setDeletedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        return ApiResponse.ok("删除成功", null);
    }

    private static PostResponse toResp(Post p) {
        PostResponse r = new PostResponse();
        r.setId(p.getId());
        r.setUserId(p.getUserId());
        r.setTitle(p.getTitle());
        r.setContent(p.getContent());
        r.setCategory(p.getCategory());
        r.setImages(p.getImages());
        r.setLocationName(p.getLocationName());
        if (p.getLocationGeom() != null) {
            r.setLng(p.getLocationGeom().getX());
            r.setLat(p.getLocationGeom().getY());
        }
        r.setViewCount(p.getViewCount());
        r.setLikeCount(p.getLikeCount());
        r.setCommentCount(p.getCommentCount());
        r.setIsAudited(p.getIsAudited());
        r.setAuditReason(p.getAuditReason());
        r.setModerationFilteredText(p.getModerationFilteredText());
        r.setModerationHitWords(p.getModerationHitWords());
        r.setModerationCheckedAt(p.getModerationCheckedAt());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }

    @GetMapping("/{id}/like-status")
    public ApiResponse<Map<String, Object>> likeStatus(Authentication authentication, @PathVariable Long id) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        boolean isOwner = principal.getId().equals(post.getUserId());
        boolean liked = likeRepository.findByUserIdAndTargetTypeAndTargetId(principal.getId(), "post", id).isPresent();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("canLike", !isOwner);
        data.put("liked", liked);
        data.put("isOwner", isOwner);
        return ApiResponse.ok(data);
    }
}
