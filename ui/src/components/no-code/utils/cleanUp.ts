function isNullOrUndefined(value: any): boolean {
    return value === null || value === undefined
}

export function removeNullAndUndefined(obj: any, keepAsIs?: (key: string) => boolean): any {
    if (Array.isArray(obj)) {
        const ar = obj
            .map(item => removeNullAndUndefined(item))
            .filter(item => isNullOrUndefined(item) === false)

        return ar.length > 0 ? ar : undefined
    }
    if (typeof obj === "object") {
        const newObj: any = {}
        let hasValue = false
        for (const key in obj) {
            const rawValue = obj[key]
            if(isNullOrUndefined(rawValue)) {
                continue
            }
            if (keepAsIs?.(key)) {
                hasValue = true
                newObj[key] = rawValue
                continue
            }
            const newVal = removeNullAndUndefined(rawValue)
            if(isNullOrUndefined(newVal)) {
                continue
            }
            hasValue = true
            newObj[key] = newVal
        }
        return hasValue ? newObj : undefined
    }
    return obj
}
