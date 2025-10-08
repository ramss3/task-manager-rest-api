const BASE_URL = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/tasks`;

export async function getTasks() {
    const res = await fetch(BASE_URL);
    if (!res.ok) throw new Error("Failed to fetch tasks");
    return res.json();
}

export async function getTaskById(id) {
    const res = await fetch(`${BASE_URL}/search/id/${id}`);
    if (!res.ok) throw new Error("Task not found");
    return res.json();
}

export async function searchTasksByTitle(keyword) {
    const res = await fetch(`${BASE_URL}/search/title/${encodeURIComponent(keyword)}`);
    if (!res.ok) throw new Error("Failed to search tasks");
    return res.json();
}

export async function searchTasksByStatus(status) {
    const res = await fetch(`${BASE_URL}/search/status/${encodeURIComponent(status)}`);
    if (!res.ok) throw new Error("Failed to search tasks");
    return res.json();
}

export async function getStatuses() {
    const res = await fetch(`${BASE_URL}/statuses`);
    if (!res.ok) throw new Error("Failed to fetch statuses");
    return res.json();
}

export async function createTask(task) {
    const res = await fetch(BASE_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(task),
    });
    if (!res.ok) throw new Error("Failed to create task");
}

export async function updateTask(id, task) {
    const res = await fetch(`${BASE_URL}/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(task),
    });
    if (!res.ok) throw new Error("Failed to update task");
}

export async function deleteTask(id) {
    const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Failed to delete task");
}