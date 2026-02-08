package task_manager_api.service;

import org.springframework.stereotype.Component;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.TeamMembership;
import task_manager_api.model.TeamRole;
import task_manager_api.model.User;

@Component
public class TeamMembershipPolicy {

    public void requireCanManageMembers(TeamMembership teamMembership, String msg) {
        if(!teamMembership.getTeamRole().canManageMembers()) {
            throw new UnauthorizedActionException(msg);
        }
    }

    public void validateRoleChange(
            User currUser,
            TeamMembership membership,
            User target,
            TeamMembership targetMembership,
            TeamRole newRole) {
        boolean currUserIsOwner = membership.getTeamRole().isOwner();
        boolean targetIsOwner = targetMembership.getTeamRole().isOwner();

        // Admin cannot assign owner
        if(!currUserIsOwner && newRole.isOwner()) {
            throw new UnauthorizedActionException("Only the owner can modify ownership role");
        }

        // Only Owner can modify Owner's membership
        if(targetIsOwner && !currUserIsOwner) {
            throw new UnauthorizedActionException("Only the owner can modify ownership role");
        }

        // Owner cannot demote themselves
        if(currUserIsOwner && currUser.equals(target) && !newRole.isOwner()) {
            throw new UnauthorizedActionException("Owner cannot demote themselves");
        }
    }

}
