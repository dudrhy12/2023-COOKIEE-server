package com.cookiee.cookieeserver.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
public class User extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto_increment
    private Long userId;  // 사용자 아이디

    @Column(nullable = false, length = 10)
    private String nickname;  // 사용자가 설정한 닉네임

    @Column(nullable = false)
    private String email;  // 이메일

    @Column(nullable = false)
    private String profileImage;  // 사용자 프로필 이미지 경로

    @Column(nullable = false, length = 50)
    private String selfDescription;  // 사용자가 설정한 한 줄 소개

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Category> categories = new ArrayList<>();
}
