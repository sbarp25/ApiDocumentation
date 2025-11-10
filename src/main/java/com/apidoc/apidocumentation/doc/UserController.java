//package com.apidoc.apidocumentation.doc;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/users")
//public class UserController {
//
//    @ApiDoc(
//            description = "Get user by ID",
//            tags = {"users", "read"}
//    )
//    @GetMapping("/{id}")
//    public User getUserById(
//            @ApiParam(description = "User ID", required = true)
//            @PathVariable Long id
//    ) {
//        return new User(id, "John Doe");
//    }
//    class User {
//        private Long id;
//        private String name;
//
//        public User(Long id, String name) {
//            this.id = id;
//            this.name = name;
//        }
//
//        public Long getId() {
//            return id;
//        }
//
//        public String getName() {
//            return name;
//        }
//    }
//
//}