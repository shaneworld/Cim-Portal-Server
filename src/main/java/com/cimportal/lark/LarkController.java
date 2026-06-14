package com.cimportal.lark;

import com.cimportal.auth.CurrentUser;
import com.cimportal.auth.CurrentUserService;
import com.cimportal.common.error.ApiException;
import com.cimportal.lark.dto.AccessRequestRequest;
import com.cimportal.lark.dto.FeedbackRequest;
import com.cimportal.link.Link;
import com.cimportal.link.LinkRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal")
public class LarkController {

    private final LarkService lark;
    private final CurrentUserService currentUser;
    private final LinkRepository linkRepo;

    public LarkController(LarkService lark, CurrentUserService currentUser, LinkRepository linkRepo) {
        this.lark = lark;
        this.currentUser = currentUser;
        this.linkRepo = linkRepo;
    }

    @PostMapping("/access-requests")
    public void accessRequest(@Valid @RequestBody AccessRequestRequest req) {
        CurrentUser u = currentUser.require();
        Link link = linkRepo.findById(req.linkId())
            .orElseThrow(() -> ApiException.notFound("链接"));
        lark.sendAccessRequest(u, link, req.reason());
    }

    @PostMapping("/feedback")
    public void feedback(@Valid @RequestBody FeedbackRequest req) {
        lark.sendFeedback(currentUser.require(), req.message());
    }
}
