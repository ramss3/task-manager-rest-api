package task_manager_api.mapper;

import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.model.TeamRole;
import task_manager_api.model.User;

public class UserMapper {

    public static UserResponseDTO toResponseDTO(User user) {
        if (user == null) return null;

        UserResponseDTO dto = new UserResponseDTO();
        dto.setUserTitle(user.getTitle());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());

        return dto;
    }

    public static UserMemberDTO toMemberDTO(User user, TeamRole role) {
        if(user == null) return null;

        UserMemberDTO dto = new UserMemberDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(role);

        return dto;
    }
}
