package com.witnesssketch;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class SketchGenerationController {
    private final HuggingFaceImageService imageService;

    public SketchGenerationController(HuggingFaceImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/refine")
    public ResponseEntity<byte[]> refine(@RequestBody RefineRequest request, HttpServletRequest servletRequest) {
        requireUser(servletRequest.getSession(false));
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refinement details are required.");
        String instructions = String.join("; ",
                request.description() == null ? "" : request.description().strip(),
                request.additionalInstructions() == null ? "" : request.additionalInstructions().strip());
        if (instructions.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description and instructions must be 500 characters or fewer.");
        }
        return imageResponse(imageService.generate(request.attributes(), instructions));
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody GenerateRequest request, HttpServletRequest servletRequest) {
        requireUser(servletRequest.getSession(false));
        if (request == null || request.description() == null
                || request.description().strip().length() < 10 || request.description().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide a fictional description between 10 and 500 characters.");
        }
        Map<String, String> attributes = Map.of(
                "faceShape", "Oval", "hairstyle", "Short", "eyebrows", "Medium", "eyeShape", "Medium",
                "noseShape", "Straight", "facialHair", "Clean-shaven", "jawline", "Soft", "expression", "Neutral");
        return imageResponse(imageService.generate(attributes, request.description()));
    }

    private static ResponseEntity<byte[]> imageResponse(HuggingFaceImageService.GeneratedImage image) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    static long requireUser(HttpSession session) {
        if (session == null || session.getAttribute(AuthController.USER_ID_SESSION_KEY) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to generate or refine sketches.");
        }
        return (Long) session.getAttribute(AuthController.USER_ID_SESSION_KEY);
    }

    public record GenerateRequest(String description) {}
    public record RefineRequest(Map<String, String> attributes, String description, String additionalInstructions) {}
}
