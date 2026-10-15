package com.example.eventapp.common;

import com.example.eventapp.entity.ApplicationStatusMaster;
import com.example.eventapp.entity.RoleMaster;
import com.example.eventapp.repository.ApplicationStatusRepository;
import com.example.eventapp.repository.RoleRepository;
import org.springframework.stereotype.Component;

/**
 * 区分値（利用者区分・申込状況）のコードから、コードマスタに登録された表示名を取得する共通部品
 * （docs/30_詳細設計/32_処理詳細設計書.md 8章）。APIは区分値をコードと表示名の両方で返す
 * （docs/20_基本設計/23_API基本設計書.md 2.8）ため、レスポンスを組み立てる各Serviceから呼ばれる。
 */
// 実行環境: サーバー側（JVM）。コードマスタ（roles・application_statuses）を参照して表示名を返す。
@Component
public class CodeNameResolver {

    private final RoleRepository roleRepository;
    private final ApplicationStatusRepository applicationStatusRepository;

    public CodeNameResolver(RoleRepository roleRepository, ApplicationStatusRepository applicationStatusRepository) {
        this.roleRepository = roleRepository;
        this.applicationStatusRepository = applicationStatusRepository;
    }

    /**
     * 利用者区分コードの表示名を返す。コードマスタに該当する行が無い場合はnullを返す。
     *
     * @param roleCode 利用者区分コード（users.role_code）
     * @return 表示名（roles.name）
     */
    public String roleName(Integer roleCode) {
        if (roleCode == null) {
            return null;
        }
        return roleRepository.findById(roleCode).map(RoleMaster::getName).orElse(null);
    }

    /**
     * 申込状況コードの表示名を返す。コードマスタに該当する行が無い場合はnullを返す。
     *
     * @param statusCode 申込状況コード（applications.status_code）
     * @return 表示名（application_statuses.name）
     */
    public String statusName(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        return applicationStatusRepository.findById(statusCode).map(ApplicationStatusMaster::getName).orElse(null);
    }
}
