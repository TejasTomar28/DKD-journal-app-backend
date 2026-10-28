package com.tejastomar.journalapp.controller;
import com.tejastomar.journalapp.dto.UserDTO;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.services.UserDetailServiceIMPL;
import com.tejastomar.journalapp.services.UserService;
import com.tejastomar.journalapp.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
@Slf4j
@Tag(name = "Public APIs")
public class PublicController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailServiceIMPL userDetailServiceIMPL;

    @Autowired
    private JwtUtil jwtUtil;

    @Operation(
            summary = "Application Health Check",
            description = "Checks whether the Journal App service is running."
    )
    @GetMapping("/health-check")
    public String healthCheck() {
        return "OK";
    }


    @Operation(
            summary = "Register New User",
            description = "Creates a new user account with USER role."
    )
    @PostMapping("/signup")
    public void signup(@RequestBody UserDTO user) {
        User newUser = new User();
        newUser.setEmail(user.getEmail());
        newUser.setUserName(user.getUserName());
        newUser.setPassword(user.getPassword());
        newUser.setSentimentAnalysis(user.isSentimentAnalysis());
        newUser.setAiReflectionEnabled(false);
        userService.saveNewUser(newUser);
    }


    @Operation(
            summary = "Generate JWT Token",
            description = "Authenticates user credentials and returns a JWT token."
    )
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user){
       try{
           authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUserName(), user.getPassword()));
           UserDetails userDetails = userDetailServiceIMPL.loadUserByUsername(user.getUserName());
           String jwt = jwtUtil.generateToken(userDetails.getUsername());
           return new ResponseEntity<>(jwt, HttpStatus.OK);
       }catch(Exception e){
           log.error("Exception occurred while creating authentication token", e);
           return new ResponseEntity<>("Incorrect Username or password",HttpStatus.BAD_REQUEST);
       }
    }
}
