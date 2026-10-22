package org.example.acm.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.dto.LoginReq;
import org.example.acm.mall.dto.RegisterReq;
import org.example.acm.mall.entity.MallUser;
import org.example.acm.mall.enums.UserStatus;
import org.example.acm.mall.mapper.MallUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final MallUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(MallUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public MallUser register(RegisterReq req) {
        if (findByEmail(req.email()) != null) {
            throw new BizException(ResultCode.CONFLICT, "该邮箱已注册");
        }
        MallUser user = new MallUser();
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname().trim());
        user.setStatus(UserStatus.NORMAL);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 上面的查询与插入之间存在竞态，唯一索引 uk_user_email 才是最终防线
            throw new BizException(ResultCode.CONFLICT, "该邮箱已注册");
        }
        return user;
    }

    public MallUser login(LoginReq req) {
        MallUser user = findByEmail(req.email());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // 不区分「账号不存在」与「密码错误」，避免被用来枚举已注册邮箱
            throw new BizException(ResultCode.UNAUTHORIZED, "邮箱或密码不正确");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        return user;
    }

    public MallUser requireById(Long id) {
        MallUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        return user;
    }

    private MallUser findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<MallUser>lambdaQuery()
                .eq(MallUser::getEmail, email.trim().toLowerCase()));
    }
}
