import { useEffect, useState } from "react";
import {
    getTasks,
    createTask,
    updateTask,
    deleteTask,
    getTaskById,
    searchTasksByTitle,
    searchTasksByStatus,
} from "./api";

function App() {
    const [tasks, setTasks] = useState([]);
    const [form, setForm] = useState({
        title: "",
        description: "",
        status: "PLANNED",
        deadline: "",
    });
    const [editId, setEditId] = useState(null);
    const [searchType, setSearchType] = useState("title");
    const [searchValue, setSearchValue] = useState("");
    const [message, setMessage] = useState("");
    const [availableStatuses, setAvailableStatuses] = useState([]); // existing ones in DB

    const allStatuses = ["PLANNED", "IN_PROGRESS", "COMPLETED", "FAILED"]; // for form

    const loadTasks = async () => {
        try {
            const data = await getTasks();
            setTasks(data);
            setMessage("");

            // Dynamically extract existing statuses from DB tasks
            const uniqueStatuses = Array.from(new Set(data.map((t) => t.status)));
            setAvailableStatuses(uniqueStatuses);
        } catch (err) {
            console.error(err);
            setMessage("Failed to load tasks");
        }
    };

    useEffect(() => {
        loadTasks();
    }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            if (editId) {
                await updateTask(editId, form);
                setMessage("Task updated successfully");
                setEditId(null);
            } else {
                await createTask(form);
                setMessage("Task added successfully");
            }
            setForm({ title: "", description: "", status: "PLANNED", deadline: "" });
            await loadTasks();
        } catch (err) {
            console.error(err);
            setMessage("Operation failed");
        }
    };

    const handleEdit = (task) => {
        setForm({
            title: task.title,
            description: task.description,
            status: task.status,
            deadline: task.deadline ? task.deadline.slice(0, 16) : "",
        });
        setEditId(task.id);
        setMessage(`Editing task #${task.id}`);
    };

    const handleDelete = async (id) => {
        if (window.confirm("Are you sure you want to delete this task?")) {
            try {
                await deleteTask(id);
                await loadTasks();
                setMessage("Task deleted successfully");
            } catch (err) {
                console.error(err);
                setMessage("Failed to delete task");
            }
        }
    };

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!searchValue.trim()) {
            await loadTasks();
            return;
        }

        try {
            if (searchType === "id") {
                const task = await getTaskById(searchValue);
                setTasks(task ? [task] : []);
            } else if (searchType === "title") {
                const results = await searchTasksByTitle(searchValue);
                setTasks(results);
            } else {
                setMessage("Invalid search type");
                return;
            }
            setMessage("Search completed");
        } catch (err) {
            console.error(err);
            setMessage("No matching task found");
            setTasks([]);
        }
    };

    // Separate handler for status filtering
    const handleStatusFilter = async (selectedStatus) => {
        try {
            const results = await searchTasksByStatus(selectedStatus);
            setTasks(results);
            setMessage(`Filtered by status: ${selectedStatus}`);
        } catch (err) {
            console.error(err);
            setMessage("Failed to filter by status");
        }
    };

    const formatStatus = (status) =>
        status
            .charAt(0)
            .toUpperCase() +
        status.slice(1).toLowerCase().replace("_", " ");

    return (
        <div
            style={{
                minHeight: "100vh",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "flex-start",
                padding: "2rem",
                fontFamily: "Arial, sans-serif",
            }}
        >
            <h1>Task Manager</h1>

            {message && <p style={{ color: "green" }}>{message}</p>}

            {/* Search by ID or Title */}
            <form
                onSubmit={handleSearch}
                style={{
                    display: "flex",
                    gap: "0.5rem",
                    alignItems: "center",
                    marginBottom: "1rem",
                }}
            >
                <select
                    value={searchType}
                    onChange={(e) => setSearchType(e.target.value)}
                >
                    <option value="id">Search by ID</option>
                    <option value="title">Search by Title</option>
                </select>
                <input
                    placeholder={`Enter ${searchType}`}
                    value={searchValue}
                    onChange={(e) => setSearchValue(e.target.value)}
                />
                <button type="submit">Search</button>
                <button
                    type="button"
                    onClick={() => {
                        setSearchValue("");
                        loadTasks();
                    }}
                >
                    Reset
                </button>
            </form>

            {/* Separate Status Filter */}
            {availableStatuses.length > 0 && (
                <div style={{ marginBottom: "1rem" }}>
                    <label htmlFor="statusFilter">Filter by Status: </label>
                    <select
                        id="statusFilter"
                        defaultValue=""
                        onChange={(e) => {
                            if (e.target.value) handleStatusFilter(e.target.value);
                        }}
                    >
                        <option value="">-- Select Status --</option>
                        {availableStatuses.map((status) => (
                            <option key={status} value={status}>
                                {formatStatus(status)}
                            </option>
                        ))}
                    </select>
                </div>
            )}

            {/* Task Form */}
            <form
                onSubmit={handleSubmit}
                style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: ".5rem",
                    width: "300px",
                    marginBottom: "1rem",
                }}
            >
                <input
                    placeholder="Title"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    required
                />
                <textarea
                    placeholder="Description"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
                <select
                    value={form.status}
                    onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                    {allStatuses.map((status) => (
                        <option key={status} value={status}>
                            {formatStatus(status)}
                        </option>
                    ))}
                </select>
                <input
                    type="datetime-local"
                    value={form.deadline}
                    onChange={(e) => setForm({ ...form, deadline: e.target.value })}
                />
                <button type="submit">{editId ? "Update Task" : "Add Task"}</button>
                {editId && (
                    <button
                        type="button"
                        onClick={() => {
                            setEditId(null);
                            setForm({
                                title: "",
                                description: "",
                                status: "PLANNED",
                                deadline: "",
                            });
                            setMessage("Edit canceled");
                        }}
                    >
                        Cancel
                    </button>
                )}
            </form>

            {/* Task List */}
            <h2>All Tasks</h2>
            <table border="1" cellPadding="5" style={{ width: "80%", marginTop: "1rem" }}>
                <thead>
                <tr>
                    <th>ID</th>
                    <th>Title</th>
                    <th>Description</th>
                    <th>Status</th>
                    <th>Deadline</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                {tasks.length > 0 ? (
                    tasks.map((t) => (
                        <tr key={t.id}>
                            <td>{t.id}</td>
                            <td>{t.title}</td>
                            <td>{t.description}</td>
                            <td>{formatStatus(t.status)}</td>
                            <td>{t.deadline?.replace("T", " ")}</td>
                            <td>
                                <button onClick={() => handleEdit(t)}>Edit</button>{" "}
                                <button onClick={() => handleDelete(t.id)}>Delete</button>
                            </td>
                        </tr>
                    ))
                ) : (
                    <tr>
                        <td colSpan="6" style={{ textAlign: "center" }}>
                            No tasks found
                        </td>
                    </tr>
                )}
                </tbody>
            </table>
        </div>
    );
}

export default App;
