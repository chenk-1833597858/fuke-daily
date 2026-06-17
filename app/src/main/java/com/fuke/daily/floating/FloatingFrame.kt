package com.fuke.daily.floating

/**
 * 悬浮窗对外唯一接口 — Phase 9
 * 独立框架，App只传数据
 *
 * 通过 FloatingWindowManager 单例管理悬浮窗生命周期：
 * - FloatingWindowManager: 管理图标/窗口的添加移除、洗牌池逻辑
 * - FloatingWindowService: Foreground Service 保活
 * - FloatingIconView: 悬浮图标自定义View（三色追逐边框）
 * - FloatingContentView: 悬浮窗内容View（原生View实现）
 *
 * 使用方式：
 * 1. MainActivity 检查 SYSTEM_ALERT_WINDOW 权限
 * 2. 有权限时启动 FloatingWindowService
 * 3. Service 创建悬浮图标，双击展开悬浮窗
 * 4. FloatingWindowManager 管理洗牌池和数据加载
 */
