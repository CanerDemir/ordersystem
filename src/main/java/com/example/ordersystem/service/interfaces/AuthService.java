package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.LoginRequest;
import com.example.ordersystem.dto.request.RegisterRequest;
import com.example.ordersystem.dto.response.LoginResponse;
import com.example.ordersystem.dto.response.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
