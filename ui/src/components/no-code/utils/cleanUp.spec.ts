import {describe, expect, it} from "vitest"
import {removeNullAndUndefined} from "./cleanUp"

describe("removeNullAndUndefined", () => {
    const task = {id: "rows", description: null, defaults: [{name: "web", cpus: null}, {name: null, cpus: null}]}

    it("drops every empty value, down to empty items of a list", () => {
        expect(removeNullAndUndefined(task)).toEqual({id: "rows", defaults: [{name: "web"}]})
    })

    it("keeps a top-level field as it is when told to, empty values included", () => {
        expect(removeNullAndUndefined(task, (key) => key === "defaults"))
            .toEqual({id: "rows", defaults: [{name: "web", cpus: null}, {name: null, cpus: null}]})
    })
})
