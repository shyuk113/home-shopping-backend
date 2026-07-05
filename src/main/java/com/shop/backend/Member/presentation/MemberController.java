package com.shop.backend.Member.presentation;

import com.shop.backend.Member.application.MemberService;
import com.shop.backend.Member.application.dto.request.MemberUpdateRequestDto;
import com.shop.backend.Member.application.dto.response.MemberResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public ResponseEntity<MemberResponseDto> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMember(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemberResponseDto> updateMember(@PathVariable Long id, @RequestBody MemberUpdateRequestDto dto) {
        MemberResponseDto updatedMember = memberService.updateMember(id, dto);
        return ResponseEntity.ok(updatedMember);
    }
}
