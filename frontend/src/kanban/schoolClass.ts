import type { Student } from '../api/students'

export interface SchoolClassGroup {
  schoolClassName: string
  students: Student[]
}

export function groupBySchoolClass(students: Student[]): SchoolClassGroup[] {
  const groups: SchoolClassGroup[] = []
  const indexByName = new Map<string, number>()

  for (const student of students) {
    const name = student.schoolClassName
    let index = indexByName.get(name)
    if (index === undefined) {
      index = groups.length
      indexByName.set(name, index)
      groups.push({ schoolClassName: name, students: [] })
    }
    groups[index].students.push(student)
  }

  return groups
}
