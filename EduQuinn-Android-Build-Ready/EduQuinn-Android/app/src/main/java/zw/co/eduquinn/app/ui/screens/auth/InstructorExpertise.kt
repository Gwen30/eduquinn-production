package zw.co.eduquinn.app.ui.screens.auth

/**
 * The "Primary expertise" options shown on instructor registration — same
 * 4 optgroups and ~54 options as the real public/signup.html form. The
 * backend only checks this field is non-blank (see RegisterViewModel's
 * doc comment), so any of these strings is a valid value; this list exists
 * purely so the native picker offers the same choices the web form does,
 * rather than a free-text field.
 */
object InstructorExpertise {
    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "School education" to listOf(
            "Primary Education",
            "Secondary Education",
            "A-Level / Advanced Secondary",
            "Mathematics",
            "English Language & Literature",
            "Sciences (General)",
            "Biology",
            "Chemistry",
            "Physics",
            "Geography",
            "History",
            "Religious Studies",
            "Languages",
        ),
        "Tertiary & professional" to listOf(
            "Engineering",
            "Chemical Engineering",
            "Mechanical Engineering",
            "Electrical & Electronic Engineering",
            "Civil Engineering",
            "Mining & Metallurgical Engineering",
            "Medicine & Health Sciences",
            "Nursing",
            "Pharmacy",
            "Law",
            "Agriculture",
            "Education & Teaching",
            "Business & Management",
            "Accounting",
            "Finance",
            "Economics",
            "Project Management",
            "Human Resources",
            "Supply Chain & Logistics",
        ),
        "Technology & digital skills" to listOf(
            "Information Technology",
            "Computer Science",
            "Programming",
            "Web Development",
            "Mobile App Development",
            "Data Science & Analytics",
            "Artificial Intelligence & Machine Learning",
            "Cybersecurity",
            "Cloud Computing",
            "Networking",
            "Database Management",
        ),
        "Creative, vocational & personal" to listOf(
            "Graphic Design",
            "UI/UX Design",
            "Photography & Video",
            "Music",
            "Marketing",
            "Digital Marketing",
            "Entrepreneurship",
            "Sales",
            "Communication & Public Speaking",
            "Personal Development",
            "Technical & Vocational Trades",
            "Other / Multidisciplinary",
        ),
    )

    /** Flattened for simple "does this value exist" checks / default selection. */
    val ALL: List<String> = GROUPS.flatMap { it.second }
}
